package com.example.carboncalculator.dto;

import java.util.List;

public record LaboratoryCompositionDTO(
        List<LaboratoryEquipmentDTO> items,
        int totalMachines,
        int modelsWithoutMonitor) {
}
