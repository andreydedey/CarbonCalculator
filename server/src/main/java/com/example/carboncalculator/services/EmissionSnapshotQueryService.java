package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.DailyEmissionDTO;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.dto.SnapshotFilter;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.SnapshotGranularity;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionPeriodSnapshotRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.specifications.EmissionSnapshotSpecification;

import lombok.RequiredArgsConstructor;

/**
 * Longitudinal series of realized emissions. Ended periods come from their frozen snapshot;
 * periods still running are calculated on demand. Rows are per day and laboratory, so the
 * laboratory filter applies to both; every granularity is aggregated from them.
 */
@Service
@RequiredArgsConstructor
public class EmissionSnapshotQueryService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.of("pt", "BR"));
    private static final DateTimeFormatter WEEK_START_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter WEEK_END_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int MAX_HISTORY_PAGE_SIZE = 20;

    private final AcademicPeriodRepository periodRepository;
    private final EmissionPeriodSnapshotRepository periodSnapshotRepository;
    private final EmissionSnapshotRepository snapshotRepository;
    private final EmissionCalculationService calculationService;
    private final Clock clock;

    private record PeriodRow(AcademicPeriod period, DailyEmissionDTO row) {}

    private record Bucket(String label, LocalDate startDate, LocalDate endDate, AcademicPeriod period,
                          List<DailyEmissionDTO> rows) {}

    @Transactional(readOnly = true)
    public List<SnapshotAggregateDTO> list(String granularity, SnapshotFilter filter) {
        return aggregate(SnapshotGranularity.from(granularity), filter);
    }

    @Transactional(readOnly = true)
    public Page<SnapshotAggregateDTO> listHistory(
            String granularity, SnapshotFilter filter, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_HISTORY_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), size);

        List<SnapshotAggregateDTO> mostRecentFirst =
                aggregate(SnapshotGranularity.from(granularity), filter).reversed();
        int from = (int) Math.min(pageRequest.getOffset(), mostRecentFirst.size());
        int to = Math.min(from + size, mostRecentFirst.size());
        return new PageImpl<>(mostRecentFirst.subList(from, to), pageRequest, mostRecentFirst.size());
    }

    private List<SnapshotAggregateDTO> aggregate(SnapshotGranularity granularity, SnapshotFilter filter) {
        Map<Object, Bucket> buckets = new LinkedHashMap<>();
        for (PeriodRow periodRow : loadRows(filter)) {
            LocalDate date = periodRow.row().date();
            Bucket bucket = switch (granularity) {
                case DAILY -> buckets.computeIfAbsent(date,
                        k -> new Bucket(date.toString(), date, date, null, new ArrayList<>()));
                case WEEKLY -> {
                    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    LocalDate sunday = monday.plusDays(6);
                    yield buckets.computeIfAbsent(monday, k -> new Bucket(
                            monday.format(WEEK_START_LABEL) + " – " + sunday.format(WEEK_END_LABEL),
                            monday, sunday, null, new ArrayList<>()));
                }
                case MONTHLY -> {
                    YearMonth month = YearMonth.from(date);
                    yield buckets.computeIfAbsent(month, k -> new Bucket(
                            month.atDay(1).format(MONTH_LABEL), month.atDay(1), month.atEndOfMonth(),
                            null, new ArrayList<>()));
                }
                case PERIOD -> {
                    AcademicPeriod period = periodRow.period();
                    yield buckets.computeIfAbsent(period.getId(), k -> new Bucket(
                            period.getName(), period.getStartDate(), period.getEndDate(), period,
                            new ArrayList<>()));
                }
            };
            bucket.rows().add(periodRow.row());
        }

        List<SnapshotAggregateDTO> result = new ArrayList<>(buckets.size());
        BigDecimal previousEmission = null;
        for (Bucket bucket : buckets.values()) {
            Map<LocalDate, List<DailyEmissionDTO>> rowsByDate = bucket.rows().stream()
                    .collect(Collectors.groupingBy(DailyEmissionDTO::date, LinkedHashMap::new, Collectors.toList()));
            BigDecimal emission = sum(bucket.rows(), DailyEmissionDTO::emissionKg);
            result.add(new SnapshotAggregateDTO(
                    bucket.label(),
                    bucket.startDate(),
                    bucket.endDate(),
                    bucket.period() != null ? bucket.period().getId() : null,
                    emission,
                    sum(bucket.rows(), DailyEmissionDTO::energyKwh),
                    (int) rowsByDate.values().stream().filter(rows -> rows.get(0).schoolDay()).count(),
                    rowsByDate.values().stream()
                            .mapToInt(rows -> rows.stream().mapToInt(DailyEmissionDTO::stationCount).sum())
                            .max().orElse(0),
                    rowsByDate.values().stream()
                            .map(rows -> rows.get(0).emissionFactor())
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(rowsByDate.size()), 6, RoundingMode.HALF_UP),
                    variationPct(emission, previousEmission)));
            previousEmission = emission;
        }
        return result;
    }

    /** Realized rows of every period, in chronological order, matching the filter. */
    private List<PeriodRow> loadRows(SnapshotFilter filter) {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate to = filter.hasDateRange() && filter.endDate().isBefore(yesterday) ? filter.endDate() : yesterday;
        LocalDate from = filter.hasDateRange() ? filter.startDate() : null;

        List<PeriodRow> rows = new ArrayList<>();
        List<AcademicPeriod> periods = periodRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicPeriod::getStartDate))
                .toList();
        for (AcademicPeriod period : periods) {
            if (period.getStartDate().isAfter(to)) continue;
            if (from != null && period.getEndDate().isBefore(from)) continue;
            for (DailyEmissionDTO row : rowsOf(period, filter, from, to)) {
                rows.add(new PeriodRow(period, row));
            }
        }
        return rows;
    }

    private List<DailyEmissionDTO> rowsOf(AcademicPeriod period, SnapshotFilter filter, LocalDate from, LocalDate to) {
        if (!periodSnapshotRepository.existsById(period.getId())) {
            return calculationService.calculateDaily(period.getId(), from, to, filter.laboratoryId());
        }

        Specification<EmissionSnapshot> spec = Specification.unrestricted();
        spec = spec.and(EmissionSnapshotSpecification.ofPeriod(period.getId()));
        if (filter.hasDateRange()) {
            spec = spec.and(EmissionSnapshotSpecification.withinDateRange(filter.startDate(), filter.endDate()));
        }
        if (filter.laboratoryId() != null) {
            spec = spec.and(EmissionSnapshotSpecification.ofLaboratory(filter.laboratoryId()));
        }
        return snapshotRepository.findAll(spec, Sort.by("snapshotDate")).stream()
                .map(EmissionSnapshotQueryService::toRow)
                .toList();
    }

    private static DailyEmissionDTO toRow(EmissionSnapshot snapshot) {
        return new DailyEmissionDTO(
                snapshot.getSnapshotDate(),
                snapshot.getLaboratory() != null ? snapshot.getLaboratory().getId() : null,
                snapshot.getDailyEnergyKwh(),
                snapshot.getDailyEmissionKg(),
                snapshot.getEmissionFactorValue(),
                snapshot.getStationCount(),
                snapshot.isSchoolDay());
    }

    private static BigDecimal sum(List<DailyEmissionDTO> rows,
                                  Function<DailyEmissionDTO, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal variationPct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
