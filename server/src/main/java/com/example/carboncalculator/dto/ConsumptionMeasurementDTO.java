package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.carboncalculator.entities.TargetType;

public record ConsumptionMeasurementDTO(
        UUID id,
        TargetType targetType,
        EquipmentModelSummary equipmentModel,
        OperatingSystemDTO operatingSystem,
        MonitorSummary monitor,
        BigDecimal averageWatts,
        int durationMinutes,
        Integer readingIntervalMinutes,
        LocalDate measurementDate,
        String conditions,
        String notes,
        OffsetDateTime createdAt) {

    public record EquipmentModelSummary(UUID id, String name) {
    }

    public record MonitorSummary(UUID id, String name) {
    }
}
