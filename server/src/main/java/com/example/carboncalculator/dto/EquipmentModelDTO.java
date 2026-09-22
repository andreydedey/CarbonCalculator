package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EquipmentModelDTO(
        UUID id,
        String name,
        String processor,
        Integer memoryGb,
        boolean hasDedicatedGpu,
        String gpuModel,
        String monitorName,
        BigDecimal monitorSizeInches,
        String monitorResolution,
        boolean hasMonitor,
        OffsetDateTime createdAt) {
}
