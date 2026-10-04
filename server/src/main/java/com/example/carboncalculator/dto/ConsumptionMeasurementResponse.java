package com.example.carboncalculator.dto;

public record ConsumptionMeasurementResponse(
        ConsumptionMeasurementDTO measurement,
        OutlierWarningDTO outlierWarning) {
}
