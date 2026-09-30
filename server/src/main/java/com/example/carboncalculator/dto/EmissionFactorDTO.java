package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EmissionFactorDTO(
        UUID id,
        short year,
        short month,
        BigDecimal value,
        String source,
        OffsetDateTime createdAt) {
}
