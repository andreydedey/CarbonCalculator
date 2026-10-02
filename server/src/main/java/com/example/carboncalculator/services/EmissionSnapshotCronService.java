package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.LaboratoryEquipment;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionFactorRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Daily cron that captures one {@link EmissionSnapshot} per institution for
 * yesterday (US-034). Runs at 01:00 every night and is idempotent — a second
 * execution on the same day for the same institution is a no-op (AC-113).
 */
@Service
@RequiredArgsConstructor
public class EmissionSnapshotCronService {

    private static final Logger log = LoggerFactory.getLogger(EmissionSnapshotCronService.class);

    private final InstitutionRepository institutionRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final EmissionFactorRepository emissionFactorRepository;
    private final LaboratoryScheduleRepository scheduleRepository;
    private final LaboratoryEquipmentRepository labEquipmentRepository;
    private final EmissionSnapshotRepository snapshotRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "0 0 1 * * *")
    public void captureYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Institution> institutions = institutionRepository.findAll();
        for (Institution institution : institutions) {
            try {
                captureForInstitution(institution, yesterday);
            } catch (Exception e) {
                log.error("Snapshot capture failed for institution {} on {}: {}",
                        institution.getId(), yesterday, e.getMessage(), e);
            }
        }
    }

    private void captureForInstitution(Institution institution, LocalDate date) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            // Set RLS tenant context for this institution
            jdbc.execute((ConnectionCallback<Void>) conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT set_config('app.current_institution', ?, true)")) {
                    ps.setString(1, institution.getId().toString());
                    ps.execute();
                }
                return null;
            });

            // Idempotency: skip if snapshot already exists (AC-113)
            if (snapshotRepository.existsBySnapshotDate(date)) {
                return null;
            }

            // Find period containing date (AC-114: skip if not in any active period)
            List<AcademicPeriod> periods = academicPeriodRepository.findAll();
            Optional<AcademicPeriod> periodOpt = periods.stream()
                    .filter(p -> !date.isBefore(p.getStartDate()) && !date.isAfter(p.getEndDate()))
                    .findFirst();
            if (periodOpt.isEmpty()) {
                return null;
            }
            AcademicPeriod period = periodOpt.get();

            // Find emission factor for date's month (AC-115: skip + warn if absent)
            Optional<EmissionFactor> factorOpt = emissionFactorRepository.findByReferenceMonth(YearMonth.from(date));
            if (factorOpt.isEmpty()) {
                log.warn("Snapshot omitted for institution {} on {}: no SIN factor for {}",
                        institution.getId(), date, YearMonth.from(date));
                return null;
            }
            EmissionFactor factor = factorOpt.get();

            // Check for holiday (AC-116)
            boolean isHoliday = period.getHolidays().stream()
                    .anyMatch(h -> h.getDate().equals(date));

            EmissionSnapshot snapshot;
            if (isHoliday) {
                snapshot = buildHolidaySnapshot(institution, period, date, factor);
            } else {
                snapshot = buildSchoolDaySnapshot(institution, period, date, factor);
            }

            snapshotRepository.save(snapshot);
            return null;
        });
    }

    private EmissionSnapshot buildHolidaySnapshot(Institution institution, AcademicPeriod period,
            LocalDate date, EmissionFactor factor) {
        return EmissionSnapshot.builder()
                .institution(institution)
                .academicPeriod(period)
                .snapshotDate(date)
                .dayOfWeek((short) date.getDayOfWeek().getValue())
                .schoolDay(false)
                .dailyEmissionKg(BigDecimal.ZERO)
                .dailyEnergyKwh(BigDecimal.ZERO)
                .emissionFactorValue(factor.getValue())
                .stationCount(0)
                .build();
    }

    private EmissionSnapshot buildSchoolDaySnapshot(Institution institution, AcademicPeriod period,
            LocalDate date, EmissionFactor factor) {
        int dow = date.getDayOfWeek().getValue();

        // Get all schedules for this period on this day of week (AC-117)
        List<LaboratorySchedule> daySchedules = scheduleRepository.findByPeriodId(period.getId())
                .stream()
                .filter(s -> s.getDayOfWeek() == dow && s.getShift().isEnabled())
                .toList();

        BigDecimal totalEnergyKwh = BigDecimal.ZERO;
        int stationCount = 0;

        // Aggregate per lab (a lab may appear in multiple shifts)
        var labHours = new java.util.LinkedHashMap<java.util.UUID, Double>();
        for (LaboratorySchedule schedule : daySchedules) {
            java.util.UUID labId = schedule.getLaboratory().getId();
            int slots = schedule.getOccupiedSlots() != null ? schedule.getOccupiedSlots().length : 0;
            double hours = slots * (double) schedule.getShift().getClassDurationMinutes() / 60.0;
            labHours.merge(labId, hours, Double::sum);
        }

        for (var entry : labHours.entrySet()) {
            java.util.UUID labId = entry.getKey();
            double hours = entry.getValue();
            List<LaboratoryEquipment> equipment = labEquipmentRepository.findByLaboratoryId(labId);

            for (LaboratoryEquipment le : equipment) {
                var model = le.getConfiguration().getEquipmentModel();
                var monitor = le.getConfiguration().getMonitor();

                int computerWatts = (model.getTdpWatts() != null ? model.getTdpWatts() : 0)
                        + (model.getGpuTdpWatts() != null ? model.getGpuTdpWatts() : 0);
                int monitorWatts = (monitor != null && monitor.getWatts() != null) ? monitor.getWatts() : 0;
                int totalWatts = computerWatts + monitorWatts;
                int qty = le.getQuantity();

                stationCount += qty;
                double energyKwh = totalWatts * hours * qty / 1000.0;
                totalEnergyKwh = totalEnergyKwh.add(
                        BigDecimal.valueOf(energyKwh).setScale(4, RoundingMode.HALF_UP));
            }
        }

        BigDecimal totalEmissionKg = totalEnergyKwh
                .multiply(factor.getValue())
                .setScale(4, RoundingMode.HALF_UP);

        return EmissionSnapshot.builder()
                .institution(institution)
                .academicPeriod(period)
                .snapshotDate(date)
                .dayOfWeek((short) dow)
                .schoolDay(true)
                .dailyEmissionKg(totalEmissionKg)
                .dailyEnergyKwh(totalEnergyKwh)
                .emissionFactorValue(factor.getValue())
                .stationCount(stationCount)
                .build();
    }
}
