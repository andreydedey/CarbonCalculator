package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConfigurationDTO(
        UUID id,
        LaboratoryEquipmentDTO.EquipmentModelSummaryDTO equipmentModel,
        String operatingSystem,
        MonitorDTO monitor,
        int usageLabCount,
        int usageStationCount,
        OffsetDateTime createdAt) {
}
