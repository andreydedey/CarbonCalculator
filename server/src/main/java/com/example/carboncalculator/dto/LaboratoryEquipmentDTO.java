package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaboratoryEquipmentDTO(
        UUID id,
        EquipmentModelSummaryDTO equipmentModel,
        String operatingSystem,
        MonitorDTO monitor,
        int quantity,
        OffsetDateTime createdAt) {

    public record EquipmentModelSummaryDTO(
            UUID id,
            String name,
            String processor,
            Integer tdpWatts,
            Integer coreCount,
            Integer memoryGb,
            boolean hasIntegratedScreen) {
    }
}
