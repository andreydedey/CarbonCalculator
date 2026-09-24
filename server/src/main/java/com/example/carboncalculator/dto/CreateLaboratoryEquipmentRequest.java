package com.example.carboncalculator.dto;

import java.util.UUID;

public record CreateLaboratoryEquipmentRequest(
        UUID configurationId,
        int quantity) {
}
