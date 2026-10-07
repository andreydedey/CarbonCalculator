package com.example.carboncalculator.dto;

import java.util.UUID;

public record LaboratoryEquipmentDTO(
        UUID id,
        UUID configurationId,
        EquipmentModelSummaryDTO equipmentModel,
        OperatingSystemDTO operatingSystem,
        MonitorDTO monitor,
        int quantity) {

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
