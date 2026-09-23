package com.example.carboncalculator.dto;

import java.util.UUID;

public record CreateLaboratoryEquipmentRequest(
        UUID equipmentModelId,
        String operatingSystem,
        UUID monitorId,
        int quantity) {
}
