package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

public record ReadinessDTO(
        boolean ready,
        List<String> missingEmissionFactors,
        List<String> laboratoriesWithoutEquipment,
        List<String> laboratoriesWithoutSchedule,
        List<ConfigurationWarning> configurationsWithoutMonitor) {

    public record ConfigurationWarning(UUID configurationId, String label,
                                       List<String> laboratoryNames) {}
}
