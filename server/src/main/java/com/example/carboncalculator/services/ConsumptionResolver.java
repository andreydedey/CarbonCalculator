package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.ConsumptionMeasurement;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.repositories.ConsumptionMeasurementRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConsumptionResolver {

    private final ConsumptionMeasurementRepository measurementRepository;

    public ResolvedConsumption resolve(Configuration config) {
        EquipmentModel model = config.getEquipmentModel();
        Monitor monitor = config.getMonitor();
        UUID modelId = model.getId();
        UUID osId = config.getOperatingSystem().getId();
        UUID monitorId = monitor != null ? monitor.getId() : null;

        // 1. COMBINED measurement
        if (monitorId != null) {
            List<ConsumptionMeasurement> combined = measurementRepository
                    .findCombinedMeasurements(modelId, osId, monitorId);
            if (!combined.isEmpty()) {
                ConsumptionMeasurement latest = combined.get(0);
                int totalWatts = latest.getAverageWatts().intValue();
                return new ResolvedConsumption(totalWatts, 0, 0, totalWatts, "measurement_combined");
            }
        }

        // 2. COMPUTER + MONITOR measurements
        List<ConsumptionMeasurement> computerMeasurements = measurementRepository
                .findComputerMeasurements(modelId, osId);
        boolean hasComputerMeasurement = !computerMeasurements.isEmpty();

        List<ConsumptionMeasurement> monitorMeasurements = monitorId != null
                ? measurementRepository.findMonitorMeasurements(monitorId)
                : List.of();
        boolean hasMonitorMeasurement = !monitorMeasurements.isEmpty();

        if (hasComputerMeasurement && hasMonitorMeasurement) {
            int computerWatts = computerMeasurements.get(0).getAverageWatts().intValue();
            int monitorWatts = monitorMeasurements.get(0).getAverageWatts().intValue();
            return new ResolvedConsumption(computerWatts + monitorWatts, computerWatts, monitorWatts,
                    computerWatts + monitorWatts, "measurement_computer+measurement_monitor");
        }

        // 3. Mixed: one measurement + one spec
        int specComputerWatts = (model.getTdpWatts() != null ? model.getTdpWatts() : 0)
                + (model.getGpuTdpWatts() != null ? model.getGpuTdpWatts() : 0);
        int specMonitorWatts = (monitor != null && monitor.getWatts() != null) ? monitor.getWatts() : 0;

        if (hasComputerMeasurement) {
            int computerWatts = computerMeasurements.get(0).getAverageWatts().intValue();
            String source = monitor != null ? "measurement_computer+specification_monitor" : "measurement_computer";
            return new ResolvedConsumption(computerWatts + specMonitorWatts, computerWatts, specMonitorWatts,
                    computerWatts + specMonitorWatts, source);
        }

        if (hasMonitorMeasurement) {
            int monitorWatts = monitorMeasurements.get(0).getAverageWatts().intValue();
            return new ResolvedConsumption(specComputerWatts + monitorWatts, specComputerWatts, monitorWatts,
                    specComputerWatts + monitorWatts, "specification_computer+measurement_monitor");
        }

        // 4. Pure specification
        return new ResolvedConsumption(specComputerWatts + specMonitorWatts, specComputerWatts, specMonitorWatts,
                specComputerWatts + specMonitorWatts, "specification");
    }

    /**
     * Parts of a configuration ("computador", "monitor") that have neither a measurement covering
     * them nor a specified power, so their consumption would silently count as zero.
     */
    public List<String> missingParts(Configuration config) {
        EquipmentModel model = config.getEquipmentModel();
        Monitor monitor = config.getMonitor();
        UUID modelId = model.getId();
        UUID osId = config.getOperatingSystem().getId();

        boolean combined = monitor != null
                && !measurementRepository.findCombinedMeasurements(modelId, osId, monitor.getId()).isEmpty();
        if (combined) {
            return List.of();
        }

        List<String> missing = new ArrayList<>();
        boolean computerCovered = model.getTdpWatts() != null
                || !measurementRepository.findComputerMeasurements(modelId, osId).isEmpty();
        if (!computerCovered) {
            missing.add("computador");
        }
        if (monitor != null) {
            boolean monitorCovered = monitor.getWatts() != null
                    || !measurementRepository.findMonitorMeasurements(monitor.getId()).isEmpty();
            if (!monitorCovered) {
                missing.add("monitor");
            }
        }
        return missing;
    }

    public record ResolvedConsumption(
            int totalWatts,
            int computerWatts,
            int monitorWatts,
            int resolvedTotalWatts,
            String source) {
    }
}
