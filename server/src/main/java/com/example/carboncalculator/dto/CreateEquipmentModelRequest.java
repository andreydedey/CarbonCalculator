package com.example.carboncalculator.dto;

import java.math.BigDecimal;

public record CreateEquipmentModelRequest(
        String name,
        String processor,
        Integer memoryGb,
        Boolean hasDedicatedGpu,
        String gpuModel,
        String monitorName,
        BigDecimal monitorSizeInches,
        String monitorResolution) {
}
