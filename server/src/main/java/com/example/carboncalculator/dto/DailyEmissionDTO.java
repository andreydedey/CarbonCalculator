package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyEmissionDTO(
        LocalDate date,
        BigDecimal energyKwh,
        BigDecimal emissionKg,
        BigDecimal emissionFactor,
        int stationCount,
        boolean schoolDay) {
}
