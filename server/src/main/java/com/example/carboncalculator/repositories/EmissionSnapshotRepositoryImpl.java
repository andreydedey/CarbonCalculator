package com.example.carboncalculator.repositories;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.example.carboncalculator.dto.SnapshotBucketDTO;
import com.example.carboncalculator.entities.SnapshotGranularity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmissionSnapshotRepositoryImpl implements EmissionSnapshotRepositoryCustom {

    private static final String AGGREGATES =
            "SUM(es.daily_emission_kg), SUM(es.daily_energy_kwh), "
            + "COUNT(*) FILTER (WHERE es.is_school_day), MAX(es.station_count), "
            + "AVG(es.emission_factor_value)";

    private final EntityManager em;

    @Override
    public List<SnapshotBucketDTO> findBucketsMostRecentFirst(
            SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate, long offset, int limit) {
        Query query = bucketQuery(granularity, startDate, endDate);
        query.setFirstResult(Math.toIntExact(offset));
        query.setMaxResults(limit);
        return toBuckets(granularity, query.getResultList());
    }

    @Override
    public List<SnapshotBucketDTO> findAllBucketsMostRecentFirst(
            SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate) {
        return toBuckets(granularity, bucketQuery(granularity, startDate, endDate).getResultList());
    }

    @Override
    public long countBuckets(SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT COUNT(*) FROM (SELECT 1 " + from(granularity) + where(startDate, endDate)
                + " GROUP BY " + groupBy(granularity) + ") buckets";
        Query query = em.createNativeQuery(sql);
        bindDateRange(query, startDate, endDate);
        return ((Number) query.getSingleResult()).longValue();
    }

    private Query bucketQuery(SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT " + bucketColumns(granularity) + ", " + AGGREGATES + " "
                + from(granularity) + where(startDate, endDate)
                + " GROUP BY " + groupBy(granularity)
                + " ORDER BY " + orderBy(granularity);
        Query query = em.createNativeQuery(sql);
        bindDateRange(query, startDate, endDate);
        return query;
    }

    private static String bucketColumns(SnapshotGranularity granularity) {
        return switch (granularity) {
            case PERIOD -> "ap.start_date, ap.end_date, ap.id, ap.name";
            default -> bucketStart(granularity) + ", NULL, NULL, NULL";
        };
    }

    private static String bucketStart(SnapshotGranularity granularity) {
        return switch (granularity) {
            case DAILY -> "es.snapshot_date";
            case WEEKLY -> "CAST(date_trunc('week', es.snapshot_date) AS date)";
            case MONTHLY -> "CAST(date_trunc('month', es.snapshot_date) AS date)";
            case PERIOD -> "ap.start_date";
        };
    }

    private static String from(SnapshotGranularity granularity) {
        return granularity == SnapshotGranularity.PERIOD
                ? "FROM emission_snapshot es JOIN academic_period ap ON ap.id = es.academic_period_id"
                : "FROM emission_snapshot es";
    }

    private static String where(LocalDate startDate, LocalDate endDate) {
        return hasDateRange(startDate, endDate)
                ? " WHERE es.snapshot_date BETWEEN :startDate AND :endDate"
                : "";
    }

    private static String groupBy(SnapshotGranularity granularity) {
        return granularity == SnapshotGranularity.PERIOD
                ? "ap.id, ap.name, ap.start_date, ap.end_date"
                : bucketStart(granularity);
    }

    private static String orderBy(SnapshotGranularity granularity) {
        return granularity == SnapshotGranularity.PERIOD
                ? "ap.start_date DESC, ap.id DESC"
                : bucketStart(granularity) + " DESC";
    }

    private static boolean hasDateRange(LocalDate startDate, LocalDate endDate) {
        return startDate != null && endDate != null;
    }

    private static void bindDateRange(Query query, LocalDate startDate, LocalDate endDate) {
        if (hasDateRange(startDate, endDate)) {
            query.setParameter("startDate", startDate);
            query.setParameter("endDate", endDate);
        }
    }

    private static List<SnapshotBucketDTO> toBuckets(SnapshotGranularity granularity, List<?> rows) {
        return rows.stream().map(row -> toBucket(granularity, (Object[]) row)).toList();
    }

    private static SnapshotBucketDTO toBucket(SnapshotGranularity granularity, Object[] row) {
        LocalDate start = toLocalDate(row[0]);
        LocalDate end = switch (granularity) {
            case DAILY -> start;
            case WEEKLY -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
            case PERIOD -> toLocalDate(row[1]);
        };
        return new SnapshotBucketDTO(
                start,
                end,
                (UUID) row[2],
                (String) row[3],
                toBigDecimal(row[4]),
                toBigDecimal(row[5]),
                ((Number) row[6]).intValue(),
                ((Number) row[7]).intValue(),
                toBigDecimal(row[8]).setScale(6, RoundingMode.HALF_UP));
    }

    private static LocalDate toLocalDate(Object value) {
        return value instanceof Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
