package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaboratoryResponse(
        UUID id,
        String name,
        boolean active,
        OffsetDateTime createdAt) {
}
