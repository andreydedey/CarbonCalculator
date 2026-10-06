package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.example.carboncalculator.entities.TargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateConsumptionMeasurementRequest(
        @NotNull(message = "Tipo de alvo é obrigatório")
        TargetType targetType,
        UUID equipmentModelId,
        UUID operatingSystemId,
        UUID monitorId,
        @NotNull(message = "Consumo médio deve ser maior que zero")
        @Positive(message = "Consumo médio deve ser maior que zero")
        BigDecimal averageWatts,
        @NotNull(message = "Duração deve ser maior que zero")
        @Positive(message = "Duração deve ser maior que zero")
        Integer durationMinutes,
        Integer readingIntervalMinutes,
        @NotNull(message = "Data da medição é obrigatória")
        LocalDate measurementDate,
        String conditions,
        String notes) {

    // Which parts identify the measured target depends on the target type:
    // COMPUTER = model + OS, MONITOR = monitor, COMBINED = model + OS + monitor

    @JsonIgnore
    @AssertTrue(message = "Modelo de equipamento é obrigatório para medições de computador ou conjuntas, "
            + "e não deve ser informado para medições de monitor")
    public boolean isEquipmentModelConsistent() {
        return targetType == null || (equipmentModelId != null) == (targetType != TargetType.MONITOR);
    }

    @JsonIgnore
    @AssertTrue(message = "Sistema operacional é obrigatório para medições de computador ou conjuntas, "
            + "e não deve ser informado para medições de monitor")
    public boolean isOperatingSystemConsistent() {
        return targetType == null || (operatingSystemId != null) == (targetType != TargetType.MONITOR);
    }

    @JsonIgnore
    @AssertTrue(message = "Monitor é obrigatório para medições de monitor ou conjuntas, "
            + "e não deve ser informado para medições de computador")
    public boolean isMonitorConsistent() {
        return targetType == null || (monitorId != null) == (targetType != TargetType.COMPUTER);
    }
}
