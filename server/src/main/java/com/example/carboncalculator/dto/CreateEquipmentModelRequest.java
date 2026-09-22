package com.example.carboncalculator.dto;

public record CreateEquipmentModelRequest(
        String name,
        String equipmentType,
        String processor,
        Integer tdpWatts,
        Integer coreCount,
        Integer memoryGb,
        String monitorName,
        Integer monitorWatts,
        String operatingSystem,
        String description) {
}
