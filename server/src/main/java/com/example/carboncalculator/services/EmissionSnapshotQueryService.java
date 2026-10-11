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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.DailyEmissionDTO;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.SnapshotGranularity;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionPeriodSnapshotRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * Longitudinal series of realized emissions. Ended periods come from their frozen snapshot;
 * periods still running are calculated on demand. Every granularity is aggregated from days.
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

    private record PeriodDay(AcademicPeriod period, DailyEmissionDTO day) {}

    private record Bucket(String label, LocalDate startDate, LocalDate endDate, AcademicPeriod period,
                          List<DailyEmissionDTO> days) {}

    @Transactional(readOnly = true)
    public List<SnapshotAggregateDTO> list(String granularity, LocalDate startDate, LocalDate endDate) {
        return aggregate(SnapshotGranularity.from(granularity), startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Page<SnapshotAggregateDTO> listHistory(
            String granularity, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_HISTORY_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), size);

        List<SnapshotAggregateDTO> mostRecentFirst =
                aggregate(SnapshotGranularity.from(granularity), startDate, endDate).reversed();
        int from = (int) Math.min(pageRequest.getOffset(), mostRecentFirst.size());
        int to = Math.min(from + size, mostRecentFirst.size());
        return new PageImpl<>(mostRecentFirst.subList(from, to), pageRequest, mostRecentFirst.size());
    }

    private List<SnapshotAggregateDTO> aggregate(SnapshotGranularity granularity, LocalDate startDate,
                                                 LocalDate endDate) {
        Map<Object, Bucket> buckets = new LinkedHashMap<>();
        for (PeriodDay periodDay : loadDays(startDate, endDate)) {
            LocalDate date = periodDay.day().date();
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
                    AcademicPeriod period = periodDay.period();
                    yield buckets.computeIfAbsent(period.getId(), k -> new Bucket(
                            period.getName(), period.getStartDate(), period.getEndDate(), period,
                            new ArrayList<>()));
                }
            };
            bucket.days().add(periodDay.day());
        }

        List<SnapshotAggregateDTO> result = new ArrayList<>(buckets.size());
        BigDecimal previousEmission = null;
        for (Bucket bucket : buckets.values()) {
            BigDecimal emission = sum(bucket.days(), DailyEmissionDTO::emissionKg);
            result.add(new SnapshotAggregateDTO(
                    bucket.label(),
                    bucket.startDate(),
                    bucket.endDate(),
                    bucket.period() != null ? bucket.period().getId() : null,
                    emission,
                    sum(bucket.days(), DailyEmissionDTO::energyKwh),
                    (int) bucket.days().stream().filter(DailyEmissionDTO::schoolDay).count(),
                    bucket.days().stream().mapToInt(DailyEmissionDTO::stationCount).max().orElse(0),
                    sum(bucket.days(), DailyEmissionDTO::emissionFactor)
                            .divide(BigDecimal.valueOf(bucket.days().size()), 6, RoundingMode.HALF_UP),
                    variationPct(emission, previousEmission)));
            previousEmission = emission;
        }
        return result;
    }

    /** Realized days of every period, in chronological order, within the optional date range. */
    private List<PeriodDay> loadDays(LocalDate startDate, LocalDate endDate) {
        boolean filtered = startDate != null && endDate != null;
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);

        List<PeriodDay> days = new ArrayList<>();
        List<AcademicPeriod> periods = periodRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicPeriod::getStartDate))
                .toList();
        for (AcademicPeriod period : periods) {
            if (period.getStartDate().isAfter(yesterday)) continue;
            if (filtered && (period.getEndDate().isBefore(startDate) || period.getStartDate().isAfter(endDate))) {
                continue;
            }
            for (DailyEmissionDTO day : daysOf(period, yesterday)) {
                if (filtered && (day.date().isBefore(startDate) || day.date().isAfter(endDate))) continue;
                days.add(new PeriodDay(period, day));
            }
        }
        return days;
    }

    private List<DailyEmissionDTO> daysOf(AcademicPeriod period, LocalDate yesterday) {
        if (!periodSnapshotRepository.existsById(period.getId())) {
            return calculationService.calculateDaily(period.getId(), yesterday);
        }
        return snapshotRepository.findByAcademicPeriodIdOrderBySnapshotDate(period.getId()).stream()
                .map(EmissionSnapshotQueryService::toDay)
                .toList();
    }

    private static DailyEmissionDTO toDay(EmissionSnapshot snapshot) {
        return new DailyEmissionDTO(
                snapshot.getSnapshotDate(),
                snapshot.getDailyEnergyKwh(),
                snapshot.getDailyEmissionKg(),
                snapshot.getEmissionFactorValue(),
                snapshot.getStationCount(),
                snapshot.isSchoolDay());
    }

    private static BigDecimal sum(List<DailyEmissionDTO> days,
                                  Function<DailyEmissionDTO, BigDecimal> value) {
        return days.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
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
