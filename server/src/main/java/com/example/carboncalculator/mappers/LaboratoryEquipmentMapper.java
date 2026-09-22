package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO.EquipmentModelSummaryDTO;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.LaboratoryEquipment;

public final class LaboratoryEquipmentMapper {

    private LaboratoryEquipmentMapper() {
    }

    public static LaboratoryEquipmentDTO toDTO(LaboratoryEquipment entity) {
        EquipmentModel model = entity.getEquipmentModel();
        return new LaboratoryEquipmentDTO(
                entity.getId(),
                new EquipmentModelSummaryDTO(
                        model.getId(),
                        model.getName(),
                        model.getProcessor(),
                        model.getTdpWatts(),
                        model.getCoreCount(),
                        model.getMemoryGb(),
                        model.getMonitorName(),
                        model.getMonitorWatts(),
                        model.getOperatingSystem(),
                        model.hasMonitor()),
                entity.getOperatingSystem(),
                entity.getQuantity(),
                entity.getCreatedAt());
    }
}
