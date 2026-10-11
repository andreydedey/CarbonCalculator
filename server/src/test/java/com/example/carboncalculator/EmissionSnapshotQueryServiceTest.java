package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.example.carboncalculator.dto.DailyEmissionDTO;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionPeriodSnapshotRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.services.EmissionCalculationService;
import com.example.carboncalculator.services.EmissionSnapshotQueryService;

class EmissionSnapshotQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private AcademicPeriodRepository periodRepository;
    private EmissionPeriodSnapshotRepository periodSnapshotRepository;
    private EmissionSnapshotRepository snapshotRepository;
    private EmissionCalculationService calculationService;
    private EmissionSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        periodRepository = mock(AcademicPeriodRepository.class);
        periodSnapshotRepository = mock(EmissionPeriodSnapshotRepository.class);
        snapshotRepository = mock(EmissionSnapshotRepository.class);
        calculationService = mock(EmissionCalculationService.class);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                ZoneId.of("America/Sao_Paulo"));
        service = new EmissionSnapshotQueryService(periodRepository, periodSnapshotRepository,
                snapshotRepository, calculationService, clock);
    }

    private static AcademicPeriod period(String name, LocalDate start, LocalDate end) {
        return AcademicPeriod.builder().id(UUID.randomUUID()).name(name).startDate(start).endDate(end).build();
    }

    private static DailyEmissionDTO day(LocalDate date, String emissionKg) {
        return new DailyEmissionDTO(date, new BigDecimal(emissionKg).multiply(BigDecimal.TEN),
                new BigDecimal(emissionKg), new BigDecimal("0.050000"), 10, true);
    }

    private void givenLivePeriod(AcademicPeriod period, DailyEmissionDTO... days) {
        when(periodRepository.findAll()).thenReturn(List.of(period));
        when(periodSnapshotRepository.existsById(period.getId())).thenReturn(false);
        when(calculationService.calculateDaily(period.getId(), TODAY.minusDays(1))).thenReturn(List.of(days));
    }

    @Test
    void deveAgregarMensalmenteSomandoDiasDoMesmoMes() {
        AcademicPeriod p = period("2025.2", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 15));
        givenLivePeriod(p,
                day(LocalDate.of(2025, 10, 6), "100"),
                day(LocalDate.of(2025, 10, 7), "120"),
                day(LocalDate.of(2025, 10, 8), "330"));

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("550").compareTo(result.get(0).totalEmissionKg()));
        assertEquals(3, result.get(0).schoolDays());
        assertEquals(LocalDate.of(2025, 10, 31), result.get(0).endDate());
    }

    @Test
    void deveCalcularVariationPctEmRelacaoAoRegistroAnteriorEPrimeiroNulo() {
        AcademicPeriod p = period("2025.2", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 15));
        givenLivePeriod(p,
                day(LocalDate.of(2025, 9, 15), "1000"),
                day(LocalDate.of(2025, 10, 15), "1120"));

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(2, result.size());
        assertNull(result.get(0).variationPct());
        assertEquals(12.0, result.get(1).variationPct().doubleValue(), 0.01);
    }

    @Test
    void deveUsarDiasCongeladosQuandoPeriodoTemSnapshot() {
        AcademicPeriod p = period("2025.1", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 7, 15));
        when(periodRepository.findAll()).thenReturn(List.of(p));
        when(periodSnapshotRepository.existsById(p.getId())).thenReturn(true);
        when(snapshotRepository.findByAcademicPeriodIdOrderBySnapshotDate(p.getId())).thenReturn(List.of(
                EmissionSnapshot.builder()
                        .snapshotDate(LocalDate.of(2025, 3, 10))
                        .dailyEnergyKwh(new BigDecimal("100"))
                        .dailyEmissionKg(new BigDecimal("5"))
                        .emissionFactorValue(new BigDecimal("0.05"))
                        .stationCount(10)
                        .schoolDay(true)
                        .build()));

        List<SnapshotAggregateDTO> result = service.list("period", null, null);

        verify(calculationService, never()).calculateDaily(any(), any());
        assertEquals(1, result.size());
        assertEquals("2025.1", result.get(0).label());
        assertEquals(p.getId(), result.get(0).periodId());
        assertEquals(0, new BigDecimal("5").compareTo(result.get(0).totalEmissionKg()));
    }

    @Test
    void deveIgnorarPeriodoQueAindaNaoComecou() {
        AcademicPeriod future = period("2027.1", TODAY.plusDays(10), TODAY.plusDays(120));
        when(periodRepository.findAll()).thenReturn(List.of(future));

        assertEquals(List.of(), service.list("monthly", null, null));
        verify(calculationService, never()).calculateDaily(any(), any());
    }

    @Test
    void deveFiltrarDiasForaDoIntervalo() {
        AcademicPeriod p = period("2025.1", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 5, 31));
        givenLivePeriod(p,
                day(LocalDate.of(2025, 3, 10), "100"),
                day(LocalDate.of(2025, 4, 10), "200"),
                day(LocalDate.of(2025, 5, 10), "300"));

        List<SnapshotAggregateDTO> result = service.list("monthly",
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("200").compareTo(result.get(0).totalEmissionKg()));
    }

    @Test
    void listHistoryDeveRetornarOrdemDoMaisRecenteParaOMaisAntigo() {
        AcademicPeriod p = period("2025.1", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        givenLivePeriod(p,
                day(LocalDate.of(2025, 1, 10), "100"),
                day(LocalDate.of(2025, 2, 10), "200"),
                day(LocalDate.of(2025, 3, 10), "300"));

        Page<SnapshotAggregateDTO> page = service.listHistory("monthly", null, null, PageRequest.of(0, 20));

        assertEquals(3, page.getContent().size());
        assertEquals(0, new BigDecimal("300").compareTo(page.getContent().get(0).totalEmissionKg()));
        assertEquals(50.0, page.getContent().get(0).variationPct().doubleValue(), 0.01);
        assertNull(page.getContent().get(2).variationPct());
    }

    @Test
    void listHistoryDeveLimitarTamanhoDaPaginaA20() {
        AcademicPeriod p = period("longo", LocalDate.of(2023, 1, 1), LocalDate.of(2025, 12, 31));
        List<DailyEmissionDTO> days = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            days.add(day(LocalDate.of(2023, 1, 1).plusMonths(i), String.valueOf(100 + i)));
        }
        givenLivePeriod(p, days.toArray(DailyEmissionDTO[]::new));

        Page<SnapshotAggregateDTO> first = service.listHistory("monthly", null, null, PageRequest.of(0, 50));
        Page<SnapshotAggregateDTO> second = service.listHistory("monthly", null, null, PageRequest.of(1, 50));

        assertEquals(20, first.getContent().size());
        assertEquals(20, first.getSize());
        assertEquals(25, first.getTotalElements());
        assertEquals(5, second.getContent().size());
        assertEquals(0, new BigDecimal("100").compareTo(second.getContent().get(4).totalEmissionKg()));
    }
}
