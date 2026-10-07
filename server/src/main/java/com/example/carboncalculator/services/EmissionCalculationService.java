package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.EmissionResultDTO;
import com.example.carboncalculator.dto.EmissionResultDTO.*;
import com.example.carboncalculator.dto.PeriodSummaryDTO;
import com.example.carboncalculator.dto.ReadinessDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratoryEquipment;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.ClassOccurrenceRepository;
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
    private final ClassOccurrenceRepository occurrenceRepository;
    private final EmissionFactorRepository emissionFactorRepository;
    private final PeriodSummaryService periodSummaryService;
    private final ConsumptionResolver consumptionResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReadinessDTO checkReadiness(UUID periodId) {
        AcademicPeriod period = getPeriod(periodId);
        List<YearMonth> months = getMonthRange(period.getStartDate(), period.getEndDate());

        // Missing emission factors
        Map<String, EmissionFactor> factorsByMonth = loadFactorsByMonth();
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

        // Parts with neither measurement nor specification would silently count as zero watts
        Map<UUID, List<String>> labNamesByConfig = new LinkedHashMap<>();
        for (Laboratory lab : labs) {
            for (LaboratoryEquipment le : labEquipmentRepository.findByLaboratoryId(lab.getId())) {
                labNamesByConfig.computeIfAbsent(le.getConfiguration().getId(), k -> new ArrayList<>())
                        .add(lab.getName());
            }
        }
        List<ReadinessDTO.ConsumptionWarning> configsWithoutConsumption = new ArrayList<>();
        for (var entry : labNamesByConfig.entrySet()) {
            Configuration config = configCache.get(entry.getKey());
            List<String> missingParts = consumptionResolver.missingParts(config);
            if (!missingParts.isEmpty()) {
                configsWithoutConsumption.add(new ReadinessDTO.ConsumptionWarning(
                        config.getId(), configLabel(config), missingParts, entry.getValue()));
            }
        }

        // A lab without a grid just adds zero; only block when no lab can be calculated at all
        boolean anyLabWithSchedule = labs.stream().anyMatch(lab -> labsWithSchedule.contains(lab.getId())
                && !labsWithoutEquipment.contains(lab.getName()));
        boolean ready = missingFactors.isEmpty() && anyLabWithSchedule && configsWithoutConsumption.isEmpty();

        return new ReadinessDTO(ready, missingFactors, labsWithoutEquipment,
                labsWithoutSchedule, configsWithoutMonitor, configsWithoutConsumption);
    }

    /**
     * Realized emissions (closed days, up to yesterday) broken down by every dimension,
     * plus the projection for the whole period (realized + remaining days by the grid).
     */
    @Transactional(readOnly = true)
    public EmissionResultDTO calculate(UUID periodId) {
        AcademicPeriod period = getPeriod(periodId);
        List<YearMonth> months = getMonthRange(period.getStartDate(), period.getEndDate());
        Map<String, EmissionFactor> factorsByMonth = loadFactorsByMonth();
        PeriodSummaryDTO summary = periodSummaryService.getSummary(periodId);
        Map<String, Integer> schoolDaysByMonth = summary.schoolDaysPerMonth().stream()
                .collect(Collectors.toMap(PeriodSummaryDTO.MonthSchoolDays::month,
                        PeriodSummaryDTO.MonthSchoolDays::schoolDays));

        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate realizedEnd = yesterday.isBefore(period.getEndDate()) ? yesterday : period.getEndDate();

        List<ClassSessionExpander.Session> sessions = ClassSessionExpander.expand(period,
                scheduleRepository.findByPeriodId(periodId),
                occurrenceRepository.findByPeriodId(periodId),
                period.getStartDate(), period.getEndDate());

        List<Laboratory> labs = laboratoryRepository.findAll();
        // Resolve each configuration's consumption once (measurements first, then specification)
        Map<UUID, Watts> wattsByConfig = new HashMap<>();
        Function<Configuration, Watts> wattsOf = config -> wattsByConfig.computeIfAbsent(
                config.getId(), id -> Watts.of(consumptionResolver.resolve(config)));

        Map<UUID, LabAccumulator> labAcc = new LinkedHashMap<>();
        for (Laboratory lab : labs) {
            labAcc.put(lab.getId(), new LabAccumulator(lab, labEquipmentRepository.findByLaboratoryId(lab.getId()),
                    wattsOf));
        }

        Totals realized = new Totals();
        Totals projected = new Totals();
        Map<String, double[]> monthAgg = new LinkedHashMap<>();
        Map<String, double[]> shiftAgg = new LinkedHashMap<>();
        Map<Integer, double[]> dowAgg = new LinkedHashMap<>();
        Map<UUID, double[]> equipModelAgg = new LinkedHashMap<>();
        Map<UUID, double[]> monitorModelAgg = new LinkedHashMap<>();
        Map<String, double[]> osAgg = new LinkedHashMap<>();
        Map<UUID, String> equipModelNames = new LinkedHashMap<>();
        Map<UUID, String> monitorModelNames = new LinkedHashMap<>();

        for (ClassSessionExpander.Session session : sessions) {
            LabAccumulator lab = labAcc.get(session.laboratoryId());
            if (lab == null) continue;
            boolean isRealized = !session.date().isAfter(realizedEnd);
            if (isRealized) lab.countStatus(session);

            int capacity = lab.capacity();
            if (session.stationsUsed() <= 0 || capacity <= 0) continue;
            EmissionFactor factor = factorsByMonth.get(YearMonth.from(session.date()).format(MONTH_FMT));
            if (factor == null) continue;
            double factorValue = factor.getValue().doubleValue();

            int stations = Math.min(session.stationsUsed(), capacity);
            double hours = session.hours();
            if (isRealized) lab.addUsage(stations, capacity, hours);

            for (LaboratoryEquipment le : lab.equipment) {
                Configuration config = le.getConfiguration();
                Watts watts = wattsOf.apply(config);
                // Stations used are split across configurations in proportion to their quantity
                double share = (double) le.getQuantity() * stations / capacity;
                double energyKwh = watts.total() * share * hours / 1000.0;
                double emissionKg = energyKwh * factorValue;

                projected.add(energyKwh, emissionKg);
                if (!isRealized) continue;

                realized.add(energyKwh, emissionKg);
                lab.addConfig(le, energyKwh, emissionKg);
                add(monthAgg, YearMonth.from(session.date()).format(MONTH_FMT), energyKwh, emissionKg);
                add(shiftAgg, session.shift().getShiftType().name(), energyKwh, emissionKg);
                add(dowAgg, session.date().getDayOfWeek().getValue(), energyKwh, emissionKg);
                add(osAgg, config.getOperatingSystem().getName(), 0, emissionKg);

                EquipmentModel model = config.getEquipmentModel();
                add(equipModelAgg, model.getId(), 0, watts.computer() * share * hours / 1000.0 * factorValue);
                equipModelNames.putIfAbsent(model.getId(), model.getName());
                Monitor monitor = config.getMonitor();
                if (monitor != null) {
                    add(monitorModelAgg, monitor.getId(), 0, watts.monitor() * share * hours / 1000.0 * factorValue);
                    monitorModelNames.putIfAbsent(monitor.getId(), monitor.getName());
                }
            }
        }

        List<MonthEmission> byMonth = months.stream()
                .map(ym -> ym.format(MONTH_FMT))
                .map(m -> {
                    double[] vals = monthAgg.getOrDefault(m, new double[2]);
                    EmissionFactor factor = factorsByMonth.get(m);
                    return new MonthEmission(m, round2(vals[0]), round2(vals[1]),
                            factor != null ? factor.getValue() : null,
                            schoolDaysByMonth.getOrDefault(m, 0));
                })
                .toList();

        List<ShiftEmission> byShift = shiftAgg.entrySet().stream()
                .map(e -> new ShiftEmission(e.getKey(), round2(e.getValue()[0]), round2(e.getValue()[1])))
                .toList();

        List<DayOfWeekEmission> byDayOfWeek = dowAgg.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DayOfWeekEmission(e.getKey(), DAY_LABELS[e.getKey()],
                        round2(e.getValue()[0]), round2(e.getValue()[1])))
                .toList();

        double total = realized.emissionKg;
        List<EquipmentModelEmission> byEquipmentModel = equipModelAgg.entrySet().stream()
                .map(e -> new EquipmentModelEmission(e.getKey(), equipModelNames.get(e.getKey()),
                        round2(e.getValue()[1]), pct(e.getValue()[1], total)))
                .sorted(Comparator.comparingDouble(EquipmentModelEmission::emissionKg).reversed())
                .toList();

        List<MonitorModelEmission> byMonitorModel = monitorModelAgg.entrySet().stream()
                .map(e -> new MonitorModelEmission(e.getKey(), monitorModelNames.get(e.getKey()),
                        round2(e.getValue()[1]), pct(e.getValue()[1], total)))
                .sorted(Comparator.comparingDouble(MonitorModelEmission::emissionKg).reversed())
                .toList();

        List<OperatingSystemEmission> byOS = osAgg.entrySet().stream()
                .map(e -> new OperatingSystemEmission(e.getKey(), round2(e.getValue()[1]), pct(e.getValue()[1], total)))
                .sorted(Comparator.comparingDouble(OperatingSystemEmission::emissionKg).reversed())
                .toList();

        List<LaboratoryEmission> byLaboratory = labAcc.values().stream().map(LabAccumulator::toDTO).toList();

        long carKm = total > 0
                ? BigDecimal.valueOf(total).divide(CAR_KM_FACTOR, 0, RoundingMode.HALF_UP).longValue()
                : 0;
        double treesNeeded = total > 0
                ? BigDecimal.valueOf(total).divide(TREE_FACTOR, 2, RoundingMode.HALF_UP).doubleValue()
                : 0;

        List<InputFactor> inputFactors = months.stream()
                .map(ym -> ym.format(MONTH_FMT))
                .filter(factorsByMonth::containsKey)
                .map(m -> {
                    EmissionFactor f = factorsByMonth.get(m);
                    return new InputFactor(m, f.getValue(), f.getSource());
                })
                .toList();

        Map<UUID, InputConsumption> inputConsumptions = new LinkedHashMap<>();
        for (LabAccumulator lab : labAcc.values()) {
            for (LaboratoryEquipment le : lab.equipment) {
                Configuration config = le.getConfiguration();
                Watts w = wattsOf.apply(config);
                inputConsumptions.putIfAbsent(config.getId(), new InputConsumption(
                        config.getId(), configLabel(config), w.computer(), w.monitor(), w.total(), w.source()));
            }
        }

        int schoolDaysTotal = 0;
        int schoolDaysElapsed = 0;
        for (LocalDate d = period.getStartDate(); !d.isAfter(period.getEndDate()); d = d.plusDays(1)) {
            LocalDate date = d;
            if (period.getShifts().stream().anyMatch(s -> ClassSessionExpander.isSchoolDayForShift(period, s, date))) {
                schoolDaysTotal++;
                if (!date.isAfter(realizedEnd)) schoolDaysElapsed++;
            }
        }
        boolean anyRealized = !realizedEnd.isBefore(period.getStartDate());

        return new EmissionResultDTO(
                period.getId(), period.getName(),
                period.getStartDate().toString(), period.getEndDate().toString(),
                anyRealized ? realizedEnd.toString() : null,
                schoolDaysElapsed, schoolDaysTotal,
                round2(realized.emissionKg), round2(realized.energyKwh),
                round2(projected.emissionKg),
                carKm, treesNeeded,
                byMonth, byLaboratory, byShift, byDayOfWeek,
                byEquipmentModel, byMonitorModel, byOS,
                inputFactors, List.copyOf(inputConsumptions.values()));
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

    // --- accumulators ---

    /**
     * Consumption of one station of a configuration. For a combined measurement only the total is
     * known, so computer and monitor stay at zero instead of inventing a split.
     */
    private record Watts(int computer, int monitor, int total, String source) {
        static Watts of(ConsumptionResolver.ResolvedConsumption resolved) {
            return new Watts(resolved.computerWatts(), resolved.monitorWatts(), resolved.totalWatts(),
                    resolved.source());
        }
    }

    private static final class Totals {
        double energyKwh;
        double emissionKg;

        void add(double energy, double emission) {
            energyKwh += energy;
            emissionKg += emission;
        }
    }

    private final class LabAccumulator {
        final Laboratory lab;
        final List<LaboratoryEquipment> equipment;
        final Function<Configuration, Watts> wattsOf;
        final Map<UUID, double[]> configAgg = new LinkedHashMap<>();
        double energyKwh;
        double emissionKg;
        double stationHours;
        double capacityHours;
        int cancelled;
        int adjusted;
        int extra;

        LabAccumulator(Laboratory lab, List<LaboratoryEquipment> equipment, Function<Configuration, Watts> wattsOf) {
            this.lab = lab;
            this.equipment = equipment;
            this.wattsOf = wattsOf;
        }

        int capacity() {
            return equipment.stream().mapToInt(LaboratoryEquipment::getQuantity).sum();
        }

        void countStatus(ClassSessionExpander.Session session) {
            switch (session.status()) {
                case CANCELLED -> cancelled++;
                case ADJUSTED -> adjusted++;
                case EXTRA -> extra++;
                default -> { }
            }
        }

        void addUsage(int stations, int capacity, double hours) {
            stationHours += stations * hours;
            capacityHours += capacity * hours;
        }

        void addConfig(LaboratoryEquipment le, double energy, double emission) {
            double[] vals = configAgg.computeIfAbsent(le.getId(), k -> new double[2]);
            vals[0] += energy;
            vals[1] += emission;
            energyKwh += energy;
            emissionKg += emission;
        }

        LaboratoryEmission toDTO() {
            List<ConfigurationEmission> configs = equipment.stream()
                    .map(le -> {
                        double[] vals = configAgg.getOrDefault(le.getId(), new double[2]);
                        Configuration config = le.getConfiguration();
                        return new ConfigurationEmission(config.getId(), configLabel(config), le.getQuantity(),
                                wattsOf.apply(config).total(), round2(vals[0]), round2(vals[1]));
                    })
                    .toList();
            double usagePct = capacityHours > 0 ? round1(stationHours / capacityHours * 100) : 0;
            return new LaboratoryEmission(lab.getId(), lab.getName(), round2(energyKwh), round2(emissionKg),
                    capacity(), round1(stationHours), usagePct, cancelled, adjusted, extra, configs);
        }
    }

    // --- helpers ---

    private static <K> void add(Map<K, double[]> agg, K key, double energy, double emission) {
        double[] vals = agg.computeIfAbsent(key, k -> new double[2]);
        vals[0] += energy;
        vals[1] += emission;
    }

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

    private Map<String, EmissionFactor> loadFactorsByMonth() {
        Map<String, EmissionFactor> map = new LinkedHashMap<>();
        for (EmissionFactor f : emissionFactorRepository.findAll()) {
            map.put(f.getReferenceMonth().format(MONTH_FMT), f);
        }
        return map;
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

    private static double pct(double part, double total) {
        return total > 0 ? round1(part / total * 100) : 0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
