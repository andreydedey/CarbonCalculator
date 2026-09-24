package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InstitutionDTO(
        UUID id,
        String name,
        String acronym,
        String city,
        String state,
        boolean active,
        long laboratoryCount,
        long equipmentCount,
        OffsetDateTime createdAt) {
}
