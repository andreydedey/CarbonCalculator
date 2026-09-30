package com.example.carboncalculator.dto;

import java.math.BigDecimal;

public record CreateEmissionFactorRequest(
        Short year,
        Short month,
        BigDecimal value,
        String source) {
}
