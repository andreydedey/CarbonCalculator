package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
