package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InstitutionResponse(
        UUID id,
        String name,
        String acronym,
        String city,
        String state,
        boolean active,
        OffsetDateTime createdAt) {
}
