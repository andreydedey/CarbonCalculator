package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO.EquipmentModelSummaryDTO;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.LaboratoryEquipment;

public final class LaboratoryEquipmentMapper {

    private LaboratoryEquipmentMapper() {
    }

    public static LaboratoryEquipmentDTO toDTO(LaboratoryEquipment entity) {
        Configuration config = entity.getConfiguration();
        EquipmentModel model = config.getEquipmentModel();
        return new LaboratoryEquipmentDTO(
                entity.getId(),
                config.getId(),
                new EquipmentModelSummaryDTO(
                        model.getId(),
                        model.getName(),
                        model.getProcessor(),
                        model.getTdpWatts(),
                        model.getCoreCount(),
                        model.getMemoryGb(),
                        model.isHasIntegratedScreen()),
                OperatingSystemMapper.toDTO(config.getOperatingSystem()),
                MonitorMapper.toDTO(config.getMonitor()),
                entity.getQuantity());
    }
}
