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
        String monitorName,
        Integer monitorWatts,
        String operatingSystem,
        String description,
        boolean hasMonitor,
        OffsetDateTime createdAt) {
}
