package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.ConsumptionMeasurementDTO;
import com.example.carboncalculator.dto.ConsumptionMeasurementDTO.EquipmentModelSummary;
import com.example.carboncalculator.dto.ConsumptionMeasurementDTO.MonitorSummary;
import com.example.carboncalculator.entities.ConsumptionMeasurement;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Monitor;

public final class ConsumptionMeasurementMapper {

    private ConsumptionMeasurementMapper() {
    }

    public static ConsumptionMeasurementDTO toDTO(ConsumptionMeasurement entity) {
        EquipmentModel model = entity.getEquipmentModel();
        Monitor monitor = entity.getMonitor();
        return new ConsumptionMeasurementDTO(
                entity.getId(),
                entity.getTargetType(),
                model != null ? new EquipmentModelSummary(model.getId(), model.getName()) : null,
                OperatingSystemMapper.toDTO(entity.getOperatingSystem()),
                monitor != null ? new MonitorSummary(monitor.getId(), monitor.getName()) : null,
                entity.getAverageWatts(),
                entity.getDurationMinutes(),
                entity.getReadingIntervalMinutes(),
                entity.getMeasurementDate(),
                entity.getConditions(),
                entity.getNotes(),
                entity.getCreatedAt());
    }
}
