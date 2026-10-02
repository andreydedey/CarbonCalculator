package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.dto.SnapshotBucketDTO;
import com.example.carboncalculator.entities.SnapshotGranularity;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmissionSnapshotQueryService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", new Locale("pt", "BR"));
    private static final DateTimeFormatter WEEK_START_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter WEEK_END_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int MAX_HISTORY_PAGE_SIZE = 20;

    private final EmissionSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public List<SnapshotAggregateDTO> list(String granularity, LocalDate startDate, LocalDate endDate) {
        SnapshotGranularity g = SnapshotGranularity.from(granularity);
        List<SnapshotBucketDTO> buckets = snapshotRepository.findAllBucketsMostRecentFirst(g, startDate, endDate);
        return toAggregatesMostRecentFirst(g, buckets).reversed();
    }

    @Transactional(readOnly = true)
    public Page<SnapshotAggregateDTO> listHistory(
            String granularity, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        SnapshotGranularity g = SnapshotGranularity.from(granularity);
        int size = Math.min(pageable.getPageSize(), MAX_HISTORY_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), size);

        List<SnapshotBucketDTO> buckets = snapshotRepository.findBucketsMostRecentFirst(
                g, startDate, endDate, pageRequest.getOffset(), size + 1);
        List<SnapshotAggregateDTO> aggregates = toAggregatesMostRecentFirst(g, buckets);
        List<SnapshotAggregateDTO> content = aggregates.subList(0, Math.min(size, aggregates.size()));

        long total = snapshotRepository.countBuckets(g, startDate, endDate);
        return new PageImpl<>(content, pageRequest, total);
    }

    private List<SnapshotAggregateDTO> toAggregatesMostRecentFirst(
            SnapshotGranularity granularity, List<SnapshotBucketDTO> buckets) {
        List<SnapshotAggregateDTO> result = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) {
            SnapshotBucketDTO bucket = buckets.get(i);
            SnapshotBucketDTO previous = i + 1 < buckets.size() ? buckets.get(i + 1) : null;
            result.add(new SnapshotAggregateDTO(
                    label(granularity, bucket),
                    bucket.startDate(),
                    bucket.endDate(),
                    bucket.periodId(),
                    bucket.totalEmissionKg(),
                    bucket.totalEnergyKwh(),
                    bucket.schoolDays(),
                    bucket.stationCount(),
                    bucket.avgEmissionFactor(),
                    variationPct(bucket, previous)));
        }
        return result;
    }

    private static String label(SnapshotGranularity granularity, SnapshotBucketDTO bucket) {
        return switch (granularity) {
            case DAILY -> bucket.startDate().toString();
            case WEEKLY -> bucket.startDate().format(WEEK_START_LABEL) + " – "
                    + bucket.endDate().format(WEEK_END_LABEL);
            case MONTHLY -> bucket.startDate().format(MONTH_LABEL);
            case PERIOD -> bucket.periodName();
        };
    }

    private static BigDecimal variationPct(SnapshotBucketDTO current, SnapshotBucketDTO previous) {
        if (previous == null || previous.totalEmissionKg().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal prev = previous.totalEmissionKg();
        return current.totalEmissionKg().subtract(prev)
                .divide(prev, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
