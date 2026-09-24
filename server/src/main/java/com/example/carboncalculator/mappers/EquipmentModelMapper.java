package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.entities.EquipmentModel;

public final class EquipmentModelMapper {

    private EquipmentModelMapper() {
    }

    public static void updateFromRequest(EquipmentModel entity, CreateEquipmentModelRequest request) {
        entity.setName(request.name());
        entity.setEquipmentType(request.equipmentType());
        entity.setProcessor(request.processor());
        entity.setTdpWatts(request.tdpWatts());
        entity.setCoreCount(request.coreCount());
        entity.setMemoryGb(request.memoryGb());
        entity.setGpuModel(request.gpuModel());
        entity.setGpuTdpWatts(request.gpuTdpWatts());
        entity.setHasIntegratedScreen(Boolean.TRUE.equals(request.hasIntegratedScreen()));
        entity.setDescription(request.description());
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
