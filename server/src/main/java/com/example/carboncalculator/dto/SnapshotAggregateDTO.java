package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One aggregated row returned by GET /api/v1/snapshots, grouped according to
 * the requested granularity (daily, weekly, monthly, period). periodId is
 * only populated for granularity=period.
 */
public record SnapshotAggregateDTO(
        String label,
        LocalDate startDate,
        LocalDate endDate,
        UUID periodId,
        BigDecimal totalEmissionKg,
        BigDecimal totalEnergyKwh,
        int schoolDays,
        int stationCount,
        BigDecimal avgEmissionFactor,
        BigDecimal variationPct) {
}
