package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Realized emissions of a period (closed days up to {@code realizedUntil}) and the projection
 * for the whole period. Every breakdown refers to the realized part. {@code realizedUntil} is
 * null when the period has not started yet.
 */
public record EmissionResultDTO(
        UUID periodId,
        String periodName,
        String startDate,
        String endDate,
        String realizedUntil,
        int schoolDaysElapsed,
        int schoolDaysTotal,
        double totalEmissionKg,
        double totalEnergyKwh,
        double projectedEmissionKg,
        long equivalentCarKm,
        double equivalentTreesNeeded,
        List<MonthEmission> byMonth,
        List<LaboratoryEmission> byLaboratory,
        List<ShiftEmission> byShift,
        List<DayOfWeekEmission> byDayOfWeek,
        List<EquipmentModelEmission> byEquipmentModel,
        List<MonitorModelEmission> byMonitorModel,
        List<OperatingSystemEmission> byOperatingSystem,
        List<InputFactor> emissionFactors,
        List<InputConsumption> consumptionSources) {

    public record MonthEmission(String month, double energyKwh, double emissionKg,
                                BigDecimal emissionFactor, int schoolDays) {}

    /**
     * {@code stationHours} sums stations used × class hours; {@code averageUsagePct} compares
     * it with the laboratory capacity over the same classes.
     */
    public record LaboratoryEmission(UUID laboratoryId, String laboratoryName,
                                     double energyKwh, double emissionKg, int stationCount,
                                     double stationHours, double averageUsagePct,
                                     int cancelledClasses, int adjustedClasses, int extraClasses,
                                     List<ConfigurationEmission> configurations) {}

    public record ConfigurationEmission(UUID configurationId, String label, int quantity,
                                        int consumptionWatts, double energyKwh, double emissionKg) {}

    public record ShiftEmission(String shiftType, double energyKwh, double emissionKg) {}

    public record DayOfWeekEmission(int dayOfWeek, String label, double energyKwh, double emissionKg) {}

    public record EquipmentModelEmission(UUID modelId, String modelName,
                                         double emissionKg, double percentage) {}

    public record MonitorModelEmission(UUID monitorId, String monitorName,
                                       double emissionKg, double percentage) {}

    public record OperatingSystemEmission(String operatingSystem,
                                          double emissionKg, double percentage) {}

    public record InputFactor(String month, BigDecimal value, String source) {}

    public record InputConsumption(UUID configurationId, String label,
                                   int computerWatts, int monitorWatts, int totalWatts) {}
}
