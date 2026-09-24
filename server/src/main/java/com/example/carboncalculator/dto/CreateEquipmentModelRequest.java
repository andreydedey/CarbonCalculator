package com.example.carboncalculator.dto;

public record CreateEquipmentModelRequest(
        String name,
        String equipmentType,
        String processor,
        Integer tdpWatts,
        Integer coreCount,
        Integer memoryGb,
        String gpuModel,
        Integer gpuTdpWatts,
        Boolean hasIntegratedScreen,
        String description) {
}
