package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LaboratoryDTO(
        UUID id,
        String name,
        String description,
        boolean active,
        long configurationCount,
        long totalStations,
        OffsetDateTime createdAt) {
}
