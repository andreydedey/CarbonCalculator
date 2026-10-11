package com.example.carboncalculator.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.carboncalculator.dto.DailyEmissionDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionPeriodSnapshot;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionPeriodSnapshotRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

/**
 * Freezes the daily emissions of each academic period once it ends, so later edits to the
 * registry (equipment, laboratories) no longer change its history. Periods still running are
 * calculated on demand.
 */
@Service
public class EmissionPeriodSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(EmissionPeriodSnapshotService.class);

    private final InstitutionRepository institutionRepository;
    private final AcademicPeriodRepository periodRepository;
    private final EmissionPeriodSnapshotRepository periodSnapshotRepository;
    private final EmissionSnapshotRepository snapshotRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EmissionCalculationService calculationService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public EmissionPeriodSnapshotService(InstitutionRepository institutionRepository,
            AcademicPeriodRepository periodRepository,
            EmissionPeriodSnapshotRepository periodSnapshotRepository,
            EmissionSnapshotRepository snapshotRepository,
            LaboratoryRepository laboratoryRepository,
            EmissionCalculationService calculationService,
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.institutionRepository = institutionRepository;
        this.periodRepository = periodRepository;
        this.periodSnapshotRepository = periodSnapshotRepository;
        this.snapshotRepository = snapshotRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.calculationService = calculationService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 1 * * *", zone = "America/Sao_Paulo")
    public void captureEndedPeriods() {
        LocalDate today = LocalDate.now(clock);
        for (Institution institution : institutionRepository.findAll()) {
            try {
                captureEndedPeriods(institution.getId(), today);
            } catch (RuntimeException e) {
                log.error("Period snapshot failed: institutionId={}", institution.getId(), e);
            }
        }
    }

    /** Snapshots every period of the institution that ended before {@code today} and has none yet. */
    public int captureEndedPeriods(UUID institutionId, LocalDate today) {
        Integer captured = transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject("SELECT set_config('app.current_institution', ?, true)",
                    String.class, institutionId.toString());

            int count = 0;
            for (AcademicPeriod period : periodRepository.findAll()) {
                if (!period.getEndDate().isBefore(today)) continue;
                if (periodSnapshotRepository.existsById(period.getId())) continue;

                List<String> missingFactors = calculationService.checkReadiness(period.getId()).missingEmissionFactors();
                if (!missingFactors.isEmpty()) {
                    log.warn("Period snapshot postponed, missing emission factors: periodId={} months={}",
                            period.getId(), missingFactors);
                    continue;
                }
                capture(institutionId, period);
                count++;
            }
            return count;
        });
        return captured != null ? captured : 0;
    }

    private void capture(UUID institutionId, AcademicPeriod period) {
        Institution institution = institutionRepository.getReferenceById(institutionId);
        List<DailyEmissionDTO> rows = calculationService.calculateDaily(
                period.getId(), null, period.getEndDate(), null);

        periodSnapshotRepository.saveAndFlush(EmissionPeriodSnapshot.builder()
                .academicPeriodId(period.getId())
                .institution(institution)
                .build());
        snapshotRepository.saveAll(rows.stream()
                .map(row -> EmissionSnapshot.builder()
                        .institution(institution)
                        .academicPeriod(period)
                        .laboratory(laboratoryRepository.getReferenceById(row.laboratoryId()))
                        .snapshotDate(row.date())
                        .dayOfWeek(row.date().getDayOfWeek())
                        .schoolDay(row.schoolDay())
                        .dailyEnergyKwh(row.energyKwh())
                        .dailyEmissionKg(row.emissionKg())
                        .emissionFactorValue(row.emissionFactor())
                        .stationCount(row.stationCount())
                        .build())
                .toList());

        log.info("Period snapshot captured: periodId={} rows={}", period.getId(), rows.size());
    }
}
