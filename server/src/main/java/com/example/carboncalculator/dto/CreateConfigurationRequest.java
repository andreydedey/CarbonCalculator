package com.example.carboncalculator.dto;

import java.util.UUID;

public record CreateConfigurationRequest(
        UUID equipmentModelId,
        String operatingSystem,
        UUID monitorId) {
}
