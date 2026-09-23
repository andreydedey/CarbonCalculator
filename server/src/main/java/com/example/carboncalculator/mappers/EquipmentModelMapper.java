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
                entity.getEquipmentType(),
                entity.getProcessor(),
                entity.getTdpWatts(),
                entity.getCoreCount(),
                entity.getMemoryGb(),
                entity.getGpuModel(),
                entity.getGpuTdpWatts(),
                entity.isHasIntegratedScreen(),
                entity.getDescription(),
                entity.getCreatedAt());
    }
}
