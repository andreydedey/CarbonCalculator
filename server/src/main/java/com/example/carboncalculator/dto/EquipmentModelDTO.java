package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EquipmentModelDTO(
        UUID id,
        String name,
        String equipmentType,
        String processor,
        Integer tdpWatts,
        Integer coreCount,
        Integer memoryGb,
        String gpuModel,
        Integer gpuTdpWatts,
        boolean hasIntegratedScreen,
        String description,
        OffsetDateTime createdAt) {
}
