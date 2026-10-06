package com.example.carboncalculator.dto;

import java.util.UUID;

public record LaboratoryDTO(
        UUID id,
        String name,
        String description,
        boolean active,
        long configurationCount,
        long totalStations) {
}
