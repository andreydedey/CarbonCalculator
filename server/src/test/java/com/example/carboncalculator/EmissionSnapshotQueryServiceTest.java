package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.services.EmissionSnapshotQueryService;

/**
 * Unit tests for EmissionSnapshotQueryService aggregation logic (US-033).
 * Uses mock repository so no database is needed.
 */
class EmissionSnapshotQueryServiceTest {

    private EmissionSnapshotRepository repository;
    private EmissionSnapshotQueryService service;

    private Institution institution;
    private AcademicPeriod period;

    @BeforeEach
    void setUp() {
        repository = mock(EmissionSnapshotRepository.class);
        service = new EmissionSnapshotQueryService(repository);

        institution = Institution.builder()
                .id(UUID.randomUUID())
                .name("Test Institution")
                .acronym("TEST")
                .state("PA")
                .build();

        period = AcademicPeriod.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .name("2025.2")
                .startDate(LocalDate.of(2025, 8, 1))
                .endDate(LocalDate.of(2025, 12, 15))
                .build();
    }

    private EmissionSnapshot snapshot(LocalDate date, String emissionKg, boolean schoolDay) {
        return EmissionSnapshot.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .academicPeriod(period)
                .snapshotDate(date)
                .dayOfWeek(date.getDayOfWeek())
                .schoolDay(schoolDay)
                .dailyEmissionKg(new BigDecimal(emissionKg))
                .dailyEnergyKwh(new BigDecimal(emissionKg).multiply(BigDecimal.valueOf(20)))
                .emissionFactorValue(new BigDecimal("0.0500"))
                .stationCount(10)
                .build();
    }

    // @spec:AC-103 Agregação mensal soma corretamente dias do mesmo mês
    @Test
    void deveAgregarMensalmenteSomandoDiasDoMesmoMes() {
        List<EmissionSnapshot> snapshots = List.of(
                snapshot(LocalDate.of(2025, 10, 6), "100", true),
                snapshot(LocalDate.of(2025, 10, 7), "120", true),
                snapshot(LocalDate.of(2025, 10, 8), "90", true),
                snapshot(LocalDate.of(2025, 10, 9), "110", true),
                snapshot(LocalDate.of(2025, 10, 10), "130", true));

        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(snapshots);

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("550").compareTo(result.get(0).totalEmissionKg()));
        assertEquals(5, result.get(0).schoolDays());
    }

    // @spec:AC-107 variationPct calculado em relação ao registro imediatamente anterior
    @Test
    void deveCalcularVariationPctEmRelacaoAoRegistroAnterior() {
        List<EmissionSnapshot> snapshots = List.of(
                snapshot(LocalDate.of(2025, 9, 15), "1000", true),
                snapshot(LocalDate.of(2025, 10, 15), "1120", true));

        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(snapshots);

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(2, result.size());
        assertNull(result.get(0).variationPct(), "first record must have null variationPct");
        assertEquals(12.0, result.get(1).variationPct().doubleValue(), 0.01);
    }

    // @spec:AC-108 Primeiro registro da série tem variationPct null
    @Test
    void devePrimeiroRegistroDaSerieTerVariationPctNulo() {
        List<EmissionSnapshot> snapshots = List.of(
                snapshot(LocalDate.of(2025, 11, 10), "300", true));

        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(snapshots);

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).variationPct());
    }

    // listHistory() feeds the "Histórico de Emissões" table: most-recent-first, paginated
    @Test
    void listHistoryDeveRetornarOrdemDoMaisRecenteParaOMaisAntigo() {
        List<EmissionSnapshot> snapshots = List.of(
                snapshot(LocalDate.of(2025, 1, 10), "100", true),
                snapshot(LocalDate.of(2025, 2, 10), "200", true),
                snapshot(LocalDate.of(2025, 3, 10), "300", true));

        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(snapshots);

        Page<SnapshotAggregateDTO> page = service.listHistory("monthly", null, null, PageRequest.of(0, 20));

        assertEquals(3, page.getContent().size());
        assertEquals(0, new BigDecimal("300").compareTo(page.getContent().get(0).totalEmissionKg()));
        assertEquals(0, new BigDecimal("100").compareTo(page.getContent().get(2).totalEmissionKg()));
    }

    // Server-enforced cap: even if the caller asks for more, each page tops out at 20 items
    @Test
    void listHistoryDeveLimitarTamanhoDaPaginaA20MesmoQuandoSolicitadoMaior() {
        List<EmissionSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            snapshots.add(snapshot(LocalDate.of(2023, 1, 1).plusMonths(i), String.valueOf(100 + i), true));
        }

        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(snapshots);

        Page<SnapshotAggregateDTO> page = service.listHistory("monthly", null, null, PageRequest.of(0, 50));

        assertEquals(20, page.getContent().size());
        assertEquals(20, page.getSize());
        assertEquals(25, page.getTotalElements());
    }
}
