package com.example.carboncalculator.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EmissionResultDTO(
        PeriodInfo period,
        double totalEmissionKg,
        double totalEnergyKwh,
        Equivalences equivalences,
        List<MonthEmission> byMonth,
        List<LaboratoryEmission> byLaboratory,
        List<ShiftEmission> byShift,
        List<DayOfWeekEmission> byDayOfWeek,
        List<EquipmentModelEmission> byEquipmentModel,
        List<MonitorModelEmission> byMonitorModel,
        List<OperatingSystemEmission> byOperatingSystem,
        Inputs inputs) {

    public record PeriodInfo(UUID id, String name, String startDate, String endDate) {}

    public record Equivalences(long carKm, double treesNeeded) {}

    public record MonthEmission(String month, double energyKwh, double emissionKg,
                                BigDecimal emissionFactor, int schoolDays) {}

    public record LaboratoryEmission(UUID laboratoryId, String laboratoryName,
                                     double energyKwh, double emissionKg, int stationCount,
                                     double computerEmissionKg, double monitorEmissionKg,
                                     List<MonthEmission> byMonth,
                                     List<ConfigurationEmission> configurations) {}

    public record ConfigurationEmission(UUID configurationId, String label, int quantity,
                                        int consumptionWatts, String consumptionSource,
                                        int computerWatts, int monitorWatts,
                                        double energyKwh, double emissionKg) {}

    public record ShiftEmission(String shiftType, double energyKwh, double emissionKg) {}

    public record DayOfWeekEmission(int dayOfWeek, String label, double energyKwh, double emissionKg) {}

    public record EquipmentModelEmission(UUID modelId, String modelName,
                                         double emissionKg, double percentage) {}

    public record MonitorModelEmission(UUID monitorId, String monitorName,
                                       double emissionKg, double percentage) {}

    public record OperatingSystemEmission(String operatingSystem,
                                          double emissionKg, double percentage) {}

    public record Inputs(List<InputFactor> emissionFactors,
                         List<InputConsumption> consumptionSources) {}

    public record InputFactor(String month, BigDecimal value, String source) {}

    public record InputConsumption(UUID configurationId, String label, String source,
                                   int computerWatts, int monitorWatts, int totalWatts) {}
}
