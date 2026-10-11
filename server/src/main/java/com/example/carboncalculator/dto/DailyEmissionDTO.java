package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailyEmissionDTO(
        LocalDate date,
        UUID laboratoryId,
        BigDecimal energyKwh,
        BigDecimal emissionKg,
        BigDecimal emissionFactor,
        int stationCount,
        boolean schoolDay) {
}
