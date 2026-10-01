package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CreateEmissionFactorRequest(
        YearMonth referenceMonth,
        BigDecimal value,
        String source) {
}
