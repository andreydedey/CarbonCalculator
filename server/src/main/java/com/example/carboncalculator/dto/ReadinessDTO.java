package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

public record ReadinessDTO(
        boolean ready,
        List<String> missingEmissionFactors,
        List<String> laboratoriesWithoutEquipment,
        List<String> laboratoriesWithoutSchedule,
        List<ConfigurationWarning> configurationsWithoutMonitor,
        List<ConsumptionWarning> configurationsWithoutConsumption) {

    public record ConfigurationWarning(UUID configurationId, String label,
                                       List<String> laboratoryNames) {}

    /** A configuration in use whose computer and/or monitor has no measurement and no specified power. */
    public record ConsumptionWarning(UUID configurationId, String label, List<String> missingParts,
                                     List<String> laboratoryNames) {}
}
