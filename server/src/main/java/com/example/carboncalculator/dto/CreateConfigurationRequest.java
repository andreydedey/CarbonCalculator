package com.example.carboncalculator.dto;

import java.util.UUID;

public record CreateConfigurationRequest(
        UUID equipmentModelId,
        UUID operatingSystemId,
        UUID monitorId) {
}
