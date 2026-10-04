package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.EmissionResultDTO;
import com.example.carboncalculator.dto.EmissionResultDTO.*;
import com.example.carboncalculator.dto.PeriodSummaryDTO;
import com.example.carboncalculator.dto.ReadinessDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodHoliday;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratoryEquipment;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionFactorRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmissionCalculationService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal CAR_KM_FACTOR = new BigDecimal("0.1667");
    private static final BigDecimal TREE_FACTOR = new BigDecimal("145.14");

    private static final String[] DAY_LABELS = {
            "", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"
    };

    private final AcademicPeriodRepository periodRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryEquipmentRepository labEquipmentRepository;
    private final LaboratoryScheduleRepository scheduleRepository;
    private final EmissionFactorRepository emissionFactorRepository;
    private final PeriodSummaryService periodSummaryService;
    private final ConsumptionResolver consumptionResolver;

    @Transactional(readOnly = true)
    public ReadinessDTO checkReadiness(UUID periodId) {
        AcademicPeriod period = getPeriod(periodId);
        List<YearMonth> months = getMonthRange(period.getStartDate(), period.getEndDate());

        // Missing emission factors
        Map<String, EmissionFactor> factorsByMonth = loadFactorsByMonth(months);
        List<String> missingFactors = months.stream()
                .map(ym -> ym.format(MONTH_FMT))
                .filter(m -> !factorsByMonth.containsKey(m))
                .toList();

        // Labs without equipment or schedule
        List<Laboratory> labs = laboratoryRepository.findAll();
        List<LaboratorySchedule> allSchedules = scheduleRepository.findByPeriodId(periodId);
        Set<UUID> labsWithSchedule = allSchedules.stream()
                .map(s -> s.getLaboratory().getId())
                .collect(Collectors.toSet());

        List<String> labsWithoutEquipment = new ArrayList<>();
        List<String> labsWithoutSchedule = new ArrayList<>();
        List<ReadinessDTO.ConfigurationWarning> configsWithoutMonitor = new ArrayList<>();

        Map<UUID, List<String>> noMonitorConfigLabNames = new LinkedHashMap<>();

        for (Laboratory lab : labs) {
            List<LaboratoryEquipment> equipment = labEquipmentRepository.findByLaboratoryId(lab.getId());
            if (equipment.isEmpty()) {
                labsWithoutEquipment.add(lab.getName());
                continue;
            }
            if (!labsWithSchedule.contains(lab.getId())) {
                labsWithoutSchedule.add(lab.getName());
            }
            for (LaboratoryEquipment le : equipment) {
                Configuration config = le.getConfiguration();
                if (config.getMonitor() == null && !config.getEquipmentModel().isHasIntegratedScreen()) {
                    noMonitorConfigLabNames.computeIfAbsent(config.getId(), k -> new ArrayList<>())
                            .add(lab.getName());
                }
            }
        }

        // Build configuration warnings (deduplicated across labs)
        Map<UUID, Configuration> configCache = new LinkedHashMap<>();
        for (Laboratory lab : labs) {
            for (LaboratoryEquipment le : labEquipmentRepository.findByLaboratoryId(lab.getId())) {
                configCache.putIfAbsent(le.getConfiguration().getId(), le.getConfiguration());
            }
        }

        for (var entry : noMonitorConfigLabNames.entrySet()) {
            Configuration config = configCache.get(entry.getKey());
            if (config != null) {
                String label = configLabel(config);
                configsWithoutMonitor.add(new ReadinessDTO.ConfigurationWarning(
                        config.getId(), label, entry.getValue()));
            }
        }

        boolean ready = missingFactors.isEmpty() && labsWithoutSchedule.isEmpty();

        return new ReadinessDTO(ready, missingFactors, labsWithoutEquipment,
                labsWithoutSchedule, configsWithoutMonitor);
    }

    @Transactional(readOnly = true)
    public EmissionResultDTO calculate(UUID periodId) {
        AcademicPeriod period = getPeriod(periodId);
        List<YearMonth> months = getMonthRange(period.getStartDate(), period.getEndDate());
        PeriodSummaryDTO summary = periodSummaryService.getSummary(periodId);

        // Load emission factors
        Map<String, EmissionFactor> factorsByMonth = loadFactorsByMonth(months);

        // School days per month from summary
        Map<String, Integer> schoolDaysByMonth = summary.schoolDaysPerMonth().stream()
                .collect(Collectors.toMap(PeriodSummaryDTO.MonthSchoolDays::month,
                        PeriodSummaryDTO.MonthSchoolDays::schoolDays));

        // Hours per month per lab from summary
        Map<UUID, Map<String, Double>> hoursByLabMonth = new LinkedHashMap<>();
        for (PeriodSummaryDTO.LaboratorySummary labSum : summary.laboratorySummaries()) {
            Map<String, Double> monthHours = labSum.hoursPerMonth().stream()
                    .collect(Collectors.toMap(PeriodSummaryDTO.MonthHours::month,
                            PeriodSummaryDTO.MonthHours::hours));
            hoursByLabMonth.put(labSum.laboratoryId(), monthHours);
        }

        // Shift proportions for decomposition
        Map<String, Double> shiftHourTotals = calculateShiftProportions(period, summary);
        Map<Integer, Double> dowHourTotals = calculateDowProportions(period, summary);

        // Iterate labs and calculate
        List<Laboratory> labs = laboratoryRepository.findAll();
        List<LaboratoryEmission> labEmissions = new ArrayList<>();

        // Aggregation accumulators
        Map<String, double[]> monthAgg = new LinkedHashMap<>(); // month -> [energy, emission]
        Map<String, double[]> shiftAgg = new LinkedHashMap<>();
        Map<Integer, double[]> dowAgg = new LinkedHashMap<>();
        Map<UUID, double[]> equipModelAgg = new LinkedHashMap<>();
        Map<UUID, double[]> monitorModelAgg = new LinkedHashMap<>();
        Map<String, double[]> osAgg = new LinkedHashMap<>();
        Map<UUID, String> equipModelNames = new LinkedHashMap<>();
        Map<UUID, String> monitorModelNames = new LinkedHashMap<>();

        List<InputConsumption> inputConsumptions = new ArrayList<>();
        Set<UUID> seenConfigs = new java.util.HashSet<>();

        double totalEnergy = 0;
        double totalEmission = 0;

        for (Laboratory lab : labs) {
            List<LaboratoryEquipment> equipment = labEquipmentRepository.findByLaboratoryId(lab.getId());
            Map<String, Double> labHours = hoursByLabMonth.getOrDefault(lab.getId(), Map.of());

            double labEnergy = 0;
            double labEmission = 0;
            int labStationCount = 0;
            List<ConfigurationEmission> labConfigs = new ArrayList<>();

            for (LaboratoryEquipment le : equipment) {
                Configuration config = le.getConfiguration();

                ConsumptionResolver.ResolvedConsumption resolved = consumptionResolver.resolve(config);
                int computerWatts = resolved.computerWatts();
                int monitorWatts = resolved.monitorWatts();
                int totalWatts = resolved.totalWatts();
                int qty = le.getQuantity();
                labStationCount += qty;

                Monitor monitor = config.getMonitor();

                // Track input consumptions (deduplicated)
                if (seenConfigs.add(config.getId())) {
                    inputConsumptions.add(new InputConsumption(
                            config.getId(), configLabel(config),
                            computerWatts, monitorWatts, totalWatts, resolved.source()));
                }

                double configEnergy = 0;
                double configEmission = 0;

                for (YearMonth ym : months) {
                    String monthKey = ym.format(MONTH_FMT);
                    double hours = labHours.getOrDefault(monthKey, 0.0);
                    EmissionFactor factor = factorsByMonth.get(monthKey);
                    if (hours <= 0 || factor == null) continue;

                    double energyKwh = totalWatts * hours * qty / 1000.0;
                    double emissionKg = energyKwh * factor.getValue().doubleValue();

                    configEnergy += energyKwh;
                    configEmission += emissionKg;

                    // Global month aggregation
                    monthAgg.computeIfAbsent(monthKey, k -> new double[2]);
                    monthAgg.get(monthKey)[0] += energyKwh;
                    monthAgg.get(monthKey)[1] += emissionKg;

                    // Equipment model aggregation
                    double computerEnergyKwh = computerWatts * hours * qty / 1000.0;
                    equipModelAgg.computeIfAbsent(model.getId(), k -> new double[1]);
                    equipModelAgg.get(model.getId())[0] += computerEnergyKwh * factor.getValue().doubleValue();
                    equipModelNames.putIfAbsent(model.getId(), model.getName());

                    // Monitor model aggregation
                    if (monitor != null) {
                        double monitorEnergyKwh = monitorWatts * hours * qty / 1000.0;
                        monitorModelAgg.computeIfAbsent(monitor.getId(), k -> new double[1]);
                        monitorModelAgg.get(monitor.getId())[0] += monitorEnergyKwh * factor.getValue().doubleValue();
                        monitorModelNames.putIfAbsent(monitor.getId(), monitor.getName());
                    }

                    // OS aggregation
                    osAgg.computeIfAbsent(config.getOperatingSystem().getName(), k -> new double[1]);
                    osAgg.get(config.getOperatingSystem().getName())[0] += emissionKg;
                }

                labEnergy += configEnergy;
                labEmission += configEmission;

                labConfigs.add(new ConfigurationEmission(
                        config.getId(), configLabel(config), qty,
                        totalWatts, round2(configEnergy), round2(configEmission)));
            }

            totalEnergy += labEnergy;
            totalEmission += labEmission;

            labEmissions.add(new LaboratoryEmission(
                    lab.getId(), lab.getName(),
                    round2(labEnergy), round2(labEmission), labStationCount,
                    labConfigs));
        }

        // Build global by-month
        List<MonthEmission> byMonth = new ArrayList<>();
        for (YearMonth ym : months) {
            String monthKey = ym.format(MONTH_FMT);
            double[] vals = monthAgg.getOrDefault(monthKey, new double[2]);
            EmissionFactor factor = factorsByMonth.get(monthKey);
            byMonth.add(new MonthEmission(monthKey, round2(vals[0]), round2(vals[1]),
                    factor != null ? factor.getValue() : null,
                    schoolDaysByMonth.getOrDefault(monthKey, 0)));
        }

        // Capture final values for lambdas
        final double finalTotalEnergy = totalEnergy;
        final double finalTotalEmission = totalEmission;

        // Build by-shift (proportional from total energy)
        List<ShiftEmission> byShift = buildShiftEmissions(shiftHourTotals, finalTotalEnergy, finalTotalEmission);

        // Build by-day-of-week (proportional)
        List<DayOfWeekEmission> byDayOfWeek = buildDowEmissions(dowHourTotals, finalTotalEnergy, finalTotalEmission);

        // Build by-equipment-model
        List<EquipmentModelEmission> byEquipmentModel = equipModelAgg.entrySet().stream()
                .map(e -> new EquipmentModelEmission(e.getKey(), equipModelNames.get(e.getKey()),
                        round2(e.getValue()[0]),
                        finalTotalEmission > 0 ? round1(e.getValue()[0] / finalTotalEmission * 100) : 0))
                .sorted((a, b) -> Double.compare(b.emissionKg(), a.emissionKg()))
                .toList();

        // Build by-monitor-model
        List<MonitorModelEmission> byMonitorModel = monitorModelAgg.entrySet().stream()
                .map(e -> new MonitorModelEmission(e.getKey(), monitorModelNames.get(e.getKey()),
                        round2(e.getValue()[0]),
                        finalTotalEmission > 0 ? round1(e.getValue()[0] / finalTotalEmission * 100) : 0))
                .sorted((a, b) -> Double.compare(b.emissionKg(), a.emissionKg()))
                .toList();

        // Build by-OS
        List<OperatingSystemEmission> byOS = osAgg.entrySet().stream()
                .map(e -> new OperatingSystemEmission(e.getKey(),
                        round2(e.getValue()[0]),
                        finalTotalEmission > 0 ? round1(e.getValue()[0] / finalTotalEmission * 100) : 0))
                .sorted((a, b) -> Double.compare(b.emissionKg(), a.emissionKg()))
                .toList();

        // Equivalences
        long carKm = finalTotalEmission > 0
                ? BigDecimal.valueOf(finalTotalEmission).divide(CAR_KM_FACTOR, 0, RoundingMode.HALF_UP).longValue()
                : 0;
        double treesNeeded = finalTotalEmission > 0
                ? BigDecimal.valueOf(finalTotalEmission).divide(TREE_FACTOR, 2, RoundingMode.HALF_UP).doubleValue()
                : 0;

        // Inputs
        List<InputFactor> inputFactors = months.stream()
                .map(ym -> ym.format(MONTH_FMT))
                .filter(factorsByMonth::containsKey)
                .map(m -> {
                    EmissionFactor f = factorsByMonth.get(m);
                    return new InputFactor(m, f.getValue(), f.getSource());
                })
                .toList();

        return new EmissionResultDTO(
                period.getId(), period.getName(),
                period.getStartDate().toString(), period.getEndDate().toString(),
                round2(finalTotalEmission),
                round2(finalTotalEnergy),
                carKm, treesNeeded,
                byMonth, labEmissions, byShift, byDayOfWeek,
                byEquipmentModel, byMonitorModel, byOS,
                inputFactors, inputConsumptions);
    }

    public String exportCsv(UUID periodId) {
        EmissionResultDTO result = calculate(periodId);
        StringBuilder sb = new StringBuilder();
        sb.append("Mês,Energia (kWh),Emissão (kgCO₂),Fator (kgCO₂/kWh),Dias Letivos\n");

        for (MonthEmission month : result.byMonth()) {
            sb.append(month.month()).append(',');
            sb.append(String.format("%.2f", month.energyKwh())).append(',');
            sb.append(String.format("%.2f", month.emissionKg())).append(',');
            sb.append(month.emissionFactor() != null ? month.emissionFactor().toPlainString() : "").append(',');
            sb.append(month.schoolDays()).append('\n');
        }

        // Per-laboratory totals
        sb.append("\nLaboratório,Estações,Energia (kWh),Emissão (kgCO₂)\n");
        for (LaboratoryEmission lab : result.byLaboratory()) {
            sb.append(escapeCsv(lab.laboratoryName())).append(',');
            sb.append(lab.stationCount()).append(',');
            sb.append(String.format("%.2f", lab.energyKwh())).append(',');
            sb.append(String.format("%.2f", lab.emissionKg())).append('\n');
        }

        return sb.toString();
    }

    // --- helpers ---

    private AcademicPeriod getPeriod(UUID periodId) {
        return periodRepository.findById(periodId)
                .orElseThrow(() -> new PeriodNotFoundException(periodId));
    }

    private List<YearMonth> getMonthRange(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!current.isAfter(last)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        return months;
    }

    private Map<String, EmissionFactor> loadFactorsByMonth(List<YearMonth> months) {
        List<EmissionFactor> factors = emissionFactorRepository.findAll();
        Map<String, EmissionFactor> map = new LinkedHashMap<>();
        for (EmissionFactor f : factors) {
            String key = f.getReferenceMonth().format(MONTH_FMT);
            map.put(key, f);
        }
        return map;
    }

    private Map<String, Double> calculateShiftProportions(AcademicPeriod period, PeriodSummaryDTO summary) {
        Map<String, Double> shiftHours = new LinkedHashMap<>();
        List<AcademicPeriodShift> enabledShifts = period.getShifts().stream()
                .filter(AcademicPeriodShift::isEnabled).toList();

        Set<LocalDate> holidays = period.getHolidays().stream()
                .map(AcademicPeriodHoliday::getDate).collect(Collectors.toSet());

        List<LaboratorySchedule> allSchedules = scheduleRepository.findByPeriodId(period.getId());

        for (AcademicPeriodShift shift : enabledShifts) {
            double totalHours = 0;
            List<LaboratorySchedule> shiftSchedules = allSchedules.stream()
                    .filter(s -> s.getShift().getId().equals(shift.getId())).toList();

            for (LaboratorySchedule schedule : shiftSchedules) {
                DayOfWeek dow = DayOfWeek.of(schedule.getDayOfWeek());
                int schoolDays = countSchoolDays(period.getStartDate(), period.getEndDate(), holidays, dow);
                int slotCount = schedule.getOccupiedSlots() != null ? schedule.getOccupiedSlots().length : 0;
                totalHours += slotCount * shift.getClassDurationMinutes() * schoolDays / 60.0;
            }

            shiftHours.merge(shift.getShiftType().name(), totalHours, Double::sum);
        }
        return shiftHours;
    }

    private Map<Integer, Double> calculateDowProportions(AcademicPeriod period, PeriodSummaryDTO summary) {
        Map<Integer, Double> dowHours = new LinkedHashMap<>();
        List<AcademicPeriodShift> enabledShifts = period.getShifts().stream()
                .filter(AcademicPeriodShift::isEnabled).toList();

        Set<LocalDate> holidays = period.getHolidays().stream()
                .map(AcademicPeriodHoliday::getDate).collect(Collectors.toSet());

        List<LaboratorySchedule> allSchedules = scheduleRepository.findByPeriodId(period.getId());

        for (AcademicPeriodShift shift : enabledShifts) {
            List<LaboratorySchedule> shiftSchedules = allSchedules.stream()
                    .filter(s -> s.getShift().getId().equals(shift.getId())).toList();

            for (LaboratorySchedule schedule : shiftSchedules) {
                DayOfWeek dow = DayOfWeek.of(schedule.getDayOfWeek());
                int schoolDays = countSchoolDays(period.getStartDate(), period.getEndDate(), holidays, dow);
                int slotCount = schedule.getOccupiedSlots() != null ? schedule.getOccupiedSlots().length : 0;
                double hours = slotCount * shift.getClassDurationMinutes() * schoolDays / 60.0;
                dowHours.merge((int) schedule.getDayOfWeek(), hours, Double::sum);
            }
        }
        return dowHours;
    }

    private int countSchoolDays(LocalDate start, LocalDate end, Set<LocalDate> holidays, DayOfWeek dow) {
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() == dow && !holidays.contains(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    private List<ShiftEmission> buildShiftEmissions(Map<String, Double> shiftHours,
                                                     double totalEnergy, double totalEmission) {
        double totalShiftHours = shiftHours.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalShiftHours <= 0) return List.of();

        return shiftHours.entrySet().stream()
                .map(e -> {
                    double proportion = e.getValue() / totalShiftHours;
                    return new ShiftEmission(e.getKey(),
                            round2(totalEnergy * proportion),
                            round2(totalEmission * proportion));
                })
                .toList();
    }

    private List<DayOfWeekEmission> buildDowEmissions(Map<Integer, Double> dowHours,
                                                       double totalEnergy, double totalEmission) {
        double totalDowHours = dowHours.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalDowHours <= 0) return List.of();

        return dowHours.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    double proportion = e.getValue() / totalDowHours;
                    return new DayOfWeekEmission(e.getKey(),
                            e.getKey() >= 1 && e.getKey() <= 7 ? DAY_LABELS[e.getKey()] : "",
                            round2(totalEnergy * proportion),
                            round2(totalEmission * proportion));
                })
                .toList();
    }

    private String configLabel(Configuration config) {
        StringBuilder sb = new StringBuilder(config.getEquipmentModel().getName());
        sb.append(" + ").append(config.getOperatingSystem().getName());
        if (config.getMonitor() != null) {
            sb.append(" + ").append(config.getMonitor().getName());
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
