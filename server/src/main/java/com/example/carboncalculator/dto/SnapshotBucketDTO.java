package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SnapshotBucketDTO(
        LocalDate startDate,
        LocalDate endDate,
        UUID periodId,
        String periodName,
        BigDecimal totalEmissionKg,
        BigDecimal totalEnergyKwh,
        int schoolDays,
        int stationCount,
        BigDecimal avgEmissionFactor) {
}
