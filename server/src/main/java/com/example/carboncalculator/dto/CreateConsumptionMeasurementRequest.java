package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.carboncalculator.entities.TargetType;

public record CreateConsumptionMeasurementRequest(
        TargetType targetType,
        UUID equipmentModelId,
        UUID operatingSystemId,
        UUID monitorId,
        BigDecimal averageWatts,
        Integer durationMinutes,
        Integer readingIntervalMinutes,
        LocalDate measurementDate,
        String conditions,
        String notes) {
}
