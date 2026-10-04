package com.example.carboncalculator.dto;

import java.math.BigDecimal;

public record OutlierWarningDTO(
        BigDecimal existingAverage,
        BigDecimal newValue,
        double deviationPercent) {
}
