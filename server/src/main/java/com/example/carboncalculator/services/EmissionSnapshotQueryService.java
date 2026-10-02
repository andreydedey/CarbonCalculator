package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates daily {@link EmissionSnapshot} records into the requested
 * granularity (daily, weekly, monthly, period) for the longitudinal view
 * (US-033). All data access is institution-scoped via RLS.
 */
@Service
@RequiredArgsConstructor
public class EmissionSnapshotQueryService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));

    private final EmissionSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public List<SnapshotAggregateDTO> list(String granularity, LocalDate startDate, LocalDate endDate) {
        List<EmissionSnapshot> snapshots = snapshotRepository.findAll(
                EmissionSnapshotRepository.withinDateRange(startDate, endDate),
                Sort.by("snapshotDate").ascending());

        return switch (granularity.toLowerCase()) {
            case "daily" -> aggregateDaily(snapshots);
            case "weekly" -> aggregateWeekly(snapshots);
            case "period" -> aggregatePeriod(snapshots);
            default -> aggregateMonthly(snapshots);
        };
    }

    // AC-106: one row per snapshot_date
    private List<SnapshotAggregateDTO> aggregateDaily(List<EmissionSnapshot> snapshots) {
        List<SnapshotAggregateDTO> result = new ArrayList<>();
        for (EmissionSnapshot s : snapshots) {
            result.add(new SnapshotAggregateDTO(
                    s.getSnapshotDate().toString(),
                    s.getSnapshotDate(),
                    s.getSnapshotDate(),
                    null,
                    s.getDailyEmissionKg(),
                    s.getDailyEnergyKwh(),
                    s.isSchoolDay() ? 1 : 0,
                    s.getStationCount(),
                    s.getEmissionFactorValue(),
                    null));
        }
        return withVariation(result);
    }

    // AC-105: group by ISO week (Monday-start via DATE_TRUNC semantics)
    private List<SnapshotAggregateDTO> aggregateWeekly(List<EmissionSnapshot> snapshots) {
        LinkedHashMap<LocalDate, List<EmissionSnapshot>> groups = new LinkedHashMap<>();
        for (EmissionSnapshot s : snapshots) {
            LocalDate monday = s.getSnapshotDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            groups.computeIfAbsent(monday, k -> new ArrayList<>()).add(s);
        }

        List<SnapshotAggregateDTO> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EmissionSnapshot>> entry : groups.entrySet()) {
            LocalDate monday = entry.getKey();
            LocalDate sunday = monday.plusDays(6);
            List<EmissionSnapshot> group = entry.getValue();

            String weekYear = monday.format(DateTimeFormatter.ofPattern("dd/MM")) + " – "
                    + sunday.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            result.add(buildAggregate(weekYear, monday, sunday, null, group));
        }
        return withVariation(result);
    }

    // AC-103: group by year-month
    private List<SnapshotAggregateDTO> aggregateMonthly(List<EmissionSnapshot> snapshots) {
        LinkedHashMap<YearMonth, List<EmissionSnapshot>> groups = new LinkedHashMap<>();
        for (EmissionSnapshot s : snapshots) {
            groups.computeIfAbsent(YearMonth.from(s.getSnapshotDate()), k -> new ArrayList<>()).add(s);
        }

        List<SnapshotAggregateDTO> result = new ArrayList<>();
        for (Map.Entry<YearMonth, List<EmissionSnapshot>> entry : groups.entrySet()) {
            YearMonth ym = entry.getKey();
            List<EmissionSnapshot> group = entry.getValue();
            String label = ym.atDay(1).format(MONTH_LABEL);
            result.add(buildAggregate(label, ym.atDay(1), ym.atEndOfMonth(), null, group));
        }
        return withVariation(result);
    }

    // AC-104: group by academic period
    private List<SnapshotAggregateDTO> aggregatePeriod(List<EmissionSnapshot> snapshots) {
        LinkedHashMap<UUID, List<EmissionSnapshot>> groups = new LinkedHashMap<>();
        LinkedHashMap<UUID, AcademicPeriod> periods = new LinkedHashMap<>();

        for (EmissionSnapshot s : snapshots) {
            UUID pid = s.getAcademicPeriod().getId();
            groups.computeIfAbsent(pid, k -> new ArrayList<>()).add(s);
            periods.putIfAbsent(pid, s.getAcademicPeriod());
        }

        // Sort groups by period start date
        List<Map.Entry<UUID, List<EmissionSnapshot>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((a, b) -> periods.get(a.getKey()).getStartDate()
                .compareTo(periods.get(b.getKey()).getStartDate()));

        List<SnapshotAggregateDTO> result = new ArrayList<>();
        for (Map.Entry<UUID, List<EmissionSnapshot>> entry : sorted) {
            UUID pid = entry.getKey();
            AcademicPeriod period = periods.get(pid);
            List<EmissionSnapshot> group = entry.getValue();
            result.add(buildAggregate(period.getName(),
                    period.getStartDate(), period.getEndDate(), pid, group));
        }
        return withVariation(result);
    }

    private SnapshotAggregateDTO buildAggregate(String label, LocalDate startDate, LocalDate endDate,
            UUID periodId, List<EmissionSnapshot> group) {
        BigDecimal totalEmission = group.stream()
                .map(EmissionSnapshot::getDailyEmissionKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEnergy = group.stream()
                .map(EmissionSnapshot::getDailyEnergyKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int schoolDays = (int) group.stream().filter(EmissionSnapshot::isSchoolDay).count();
        int maxStations = group.stream().mapToInt(EmissionSnapshot::getStationCount).max().orElse(0);
        BigDecimal avgFactor = group.stream()
                .map(EmissionSnapshot::getEmissionFactorValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(group.size()), 6, RoundingMode.HALF_UP);

        return new SnapshotAggregateDTO(label, startDate, endDate, periodId,
                totalEmission, totalEnergy, schoolDays, maxStations, avgFactor, null);
    }

    // AC-107, AC-108: compute variationPct relative to the previous bucket
    private List<SnapshotAggregateDTO> withVariation(List<SnapshotAggregateDTO> items) {
        List<SnapshotAggregateDTO> result = new ArrayList<>(items.size());
        BigDecimal prev = null;
        for (SnapshotAggregateDTO item : items) {
            BigDecimal current = item.totalEmissionKg();
            BigDecimal variationPct = null;
            if (prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
                variationPct = current.subtract(prev)
                        .divide(prev, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            result.add(new SnapshotAggregateDTO(
                    item.label(), item.startDate(), item.endDate(), item.periodId(),
                    item.totalEmissionKg(), item.totalEnergyKwh(),
                    item.schoolDays(), item.stationCount(), item.avgEmissionFactor(),
                    variationPct));
            prev = current;
        }
        return result;
    }
}
