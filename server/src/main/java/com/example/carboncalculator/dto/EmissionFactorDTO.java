package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record EmissionFactorDTO(
        UUID id,
        YearMonth referenceMonth,
        BigDecimal value,
        String source) {
}
