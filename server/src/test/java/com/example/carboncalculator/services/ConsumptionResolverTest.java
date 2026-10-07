package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.ConsumptionMeasurement;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.entities.OperatingSystem;
import com.example.carboncalculator.repositories.ConsumptionMeasurementRepository;

/**
 * Source hierarchy for one configuration: combined measurement > computer and monitor measurements >
 * one measurement + specification > specification.
 */
class ConsumptionResolverTest {

    private ConsumptionMeasurementRepository repository;
    private ConsumptionResolver resolver;

    // Computer spec: 65 W CPU + 0 GPU; monitor spec: 21 W
    private final EquipmentModel desktop = EquipmentModel.builder().id(UUID.randomUUID()).name("Desktop").tdpWatts(65).build();
    private final EquipmentModel workstation = EquipmentModel.builder().id(UUID.randomUUID()).name("Workstation").tdpWatts(125).gpuTdpWatts(75).build();
    private final OperatingSystem windows = OperatingSystem.builder().id(UUID.randomUUID()).name("Windows 10").build();
    private final OperatingSystem linux = OperatingSystem.builder().id(UUID.randomUUID()).name("Linux").build();
    private final Monitor monitorA = Monitor.builder().id(UUID.randomUUID()).name("Monitor A").watts(21).build();
    private final Monitor monitorB = Monitor.builder().id(UUID.randomUUID()).name("Monitor B").watts(18).build();

    @BeforeEach
    void setUp() {
        repository = mock(ConsumptionMeasurementRepository.class);
        resolver = new ConsumptionResolver(repository);
    }

    private static Configuration config(EquipmentModel model, OperatingSystem os, Monitor monitor) {
        return Configuration.builder().id(UUID.randomUUID()).equipmentModel(model).operatingSystem(os).monitor(monitor).build();
    }

    private static List<ConsumptionMeasurement> measured(String watts) {
        return List.of(ConsumptionMeasurement.builder().averageWatts(new BigDecimal(watts)).build());
    }

    // @spec:AC-121
    @Test
    void medicaoDoMonitorValeParaTodasAsConfiguracoesComAqueleMonitor() {
        when(repository.findMonitorMeasurements(monitorA.getId())).thenReturn(measured("30"));

        var desktopResult = resolver.resolve(config(desktop, windows, monitorA));
        var workstationResult = resolver.resolve(config(workstation, linux, monitorA));

        assertEquals(30, desktopResult.monitorWatts());
        assertEquals(30, workstationResult.monitorWatts());
        assertEquals("specification_computer+measurement_monitor", desktopResult.source());
    }

    // @spec:AC-122
    @Test
    void medicaoConjuntaValeSoParaACombinacaoExata() {
        when(repository.findCombinedMeasurements(desktop.getId(), windows.getId(), monitorA.getId()))
                .thenReturn(measured("110"));

        var exact = resolver.resolve(config(desktop, windows, monitorA));
        var otherMonitor = resolver.resolve(config(desktop, windows, monitorB));

        assertEquals(110, exact.totalWatts());
        assertEquals("measurement_combined", exact.source());
        assertEquals(65 + 18, otherMonitor.totalWatts());
        assertEquals("specification", otherMonitor.source());
    }

    // @spec:AC-123
    @Test
    void medicaoConjuntaTemPrioridadeENaoSomaOMonitorDeNovo() {
        when(repository.findCombinedMeasurements(desktop.getId(), windows.getId(), monitorA.getId()))
                .thenReturn(measured("110"));
        when(repository.findComputerMeasurements(desktop.getId(), windows.getId())).thenReturn(measured("90"));
        when(repository.findMonitorMeasurements(monitorA.getId())).thenReturn(measured("25"));

        var result = resolver.resolve(config(desktop, windows, monitorA));

        assertEquals(110, result.totalWatts());
        assertEquals(0, result.computerWatts());
        assertEquals(0, result.monitorWatts());
        assertEquals("measurement_combined", result.source());
    }

    // @spec:AC-126
    @Test
    void semMedicaoUsaEspecificacaoDeCpuGpuEMonitor() {
        var result = resolver.resolve(config(workstation, linux, monitorA));

        assertEquals(125 + 75, result.computerWatts());
        assertEquals(21, result.monitorWatts());
        assertEquals(125 + 75 + 21, result.totalWatts());
        assertEquals("specification", result.source());
    }

    // @spec:AC-127
    @Test
    void medicaoDeOutroSistemaOperacionalNaoEReaproveitada() {
        when(repository.findComputerMeasurements(desktop.getId(), windows.getId())).thenReturn(measured("90"));

        var linuxResult = resolver.resolve(config(desktop, linux, monitorA));

        assertEquals(65, linuxResult.computerWatts());
        assertEquals("specification", linuxResult.source());
    }

    // @spec:AC-128
    @Test
    void medicaoDoComputadorComEspecificacaoDoMonitor() {
        when(repository.findComputerMeasurements(desktop.getId(), windows.getId())).thenReturn(measured("90"));

        var withMonitor = resolver.resolve(config(desktop, windows, monitorA));
        var withoutMonitor = resolver.resolve(config(desktop, windows, null));

        assertEquals(90, withMonitor.computerWatts());
        assertEquals(21, withMonitor.monitorWatts());
        assertEquals("measurement_computer+specification_monitor", withMonitor.source());
        assertEquals("measurement_computer", withoutMonitor.source());
    }

    @Test
    void parteSemMedicaoNemEspecificacaoEApontada() {
        EquipmentModel noSpec = EquipmentModel.builder().id(UUID.randomUUID()).name("Sem TDP").build();
        Monitor noWatts = Monitor.builder().id(UUID.randomUUID()).name("Sem potência").build();

        assertEquals(List.of("computador", "monitor"), resolver.missingParts(config(noSpec, linux, noWatts)));

        when(repository.findComputerMeasurements(noSpec.getId(), linux.getId())).thenReturn(measured("80"));
        assertEquals(List.of("monitor"), resolver.missingParts(config(noSpec, linux, noWatts)));
        assertTrue(resolver.missingParts(config(desktop, windows, monitorA)).isEmpty());
    }
}
