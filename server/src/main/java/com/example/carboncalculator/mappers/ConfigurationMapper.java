package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.ConfigurationDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO.EquipmentModelSummaryDTO;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EquipmentModel;

public final class ConfigurationMapper {

    private ConfigurationMapper() {
    }

    public static ConfigurationDTO toDTO(Configuration entity, int labCount, int stationCount) {
        EquipmentModel model = entity.getEquipmentModel();
        return new ConfigurationDTO(
                entity.getId(),
                new EquipmentModelSummaryDTO(
                        model.getId(),
                        model.getName(),
                        model.getProcessor(),
                        model.getTdpWatts(),
                        model.getCoreCount(),
                        model.getMemoryGb(),
                        model.isHasIntegratedScreen()),
                entity.getOperatingSystem(),
                MonitorMapper.toDTO(entity.getMonitor()),
                labCount,
                stationCount,
                entity.getCreatedAt());
    }
}
