package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.entities.EquipmentModel;

public final class EquipmentModelMapper {

    private EquipmentModelMapper() {
    }

    public static EquipmentModelDTO toDTO(EquipmentModel entity) {
        return new EquipmentModelDTO(
                entity.getId(),
                entity.getName(),
                entity.getProcessor(),
                entity.getMemoryGb(),
                entity.isHasDedicatedGpu(),
                entity.getGpuModel(),
                entity.getMonitorName(),
                entity.getMonitorSizeInches(),
                entity.getMonitorResolution(),
                entity.hasMonitor(),
                entity.getCreatedAt());
    }
}
