package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.dto.SnapshotBucketDTO;
import com.example.carboncalculator.entities.SnapshotGranularity;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.services.EmissionSnapshotQueryService;

class EmissionSnapshotQueryServiceTest {

    private EmissionSnapshotRepository repository;
    private EmissionSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmissionSnapshotRepository.class);
        service = new EmissionSnapshotQueryService(repository);
    }

    private SnapshotBucketDTO month(LocalDate firstDay, String emissionKg, int schoolDays) {
        return new SnapshotBucketDTO(
                firstDay,
                firstDay.withDayOfMonth(firstDay.lengthOfMonth()),
                null,
                null,
                new BigDecimal(emissionKg),
                new BigDecimal(emissionKg).multiply(BigDecimal.valueOf(20)),
                schoolDays,
                10,
                new BigDecimal("0.050000"));
    }

    private void givenAllBuckets(SnapshotBucketDTO... mostRecentFirst) {
        when(repository.findAllBucketsMostRecentFirst(eq(SnapshotGranularity.MONTHLY), any(), any()))
                .thenReturn(List.of(mostRecentFirst));
    }

    // @spec:AC-103 Agregação mensal soma corretamente dias do mesmo mês
    @Test
    void deveAgregarMensalmenteSomandoDiasDoMesmoMes() {
        givenAllBuckets(month(LocalDate.of(2025, 10, 1), "550", 5));

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("550").compareTo(result.get(0).totalEmissionKg()));
        assertEquals(5, result.get(0).schoolDays());
    }

    // @spec:AC-107 variationPct calculado em relação ao registro imediatamente anterior
    @Test
    void deveCalcularVariationPctEmRelacaoAoRegistroAnterior() {
        givenAllBuckets(
                month(LocalDate.of(2025, 10, 1), "1120", 1),
                month(LocalDate.of(2025, 9, 1), "1000", 1));

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(2, result.size());
        assertNull(result.get(0).variationPct());
        assertEquals(12.0, result.get(1).variationPct().doubleValue(), 0.01);
    }

    // @spec:AC-108 Primeiro registro da série tem variationPct null
    @Test
    void devePrimeiroRegistroDaSerieTerVariationPctNulo() {
        givenAllBuckets(month(LocalDate.of(2025, 11, 1), "300", 1));

        List<SnapshotAggregateDTO> result = service.list("monthly", null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).variationPct());
    }

    @Test
    void listHistoryDeveRetornarOrdemDoMaisRecenteParaOMaisAntigo() {
        when(repository.findBucketsMostRecentFirst(eq(SnapshotGranularity.MONTHLY), any(), any(), eq(0L), eq(21)))
                .thenReturn(List.of(
                        month(LocalDate.of(2025, 3, 1), "300", 1),
                        month(LocalDate.of(2025, 2, 1), "200", 1),
                        month(LocalDate.of(2025, 1, 1), "100", 1)));
        when(repository.countBuckets(eq(SnapshotGranularity.MONTHLY), any(), any())).thenReturn(3L);

        Page<SnapshotAggregateDTO> page = service.listHistory("monthly", null, null, PageRequest.of(0, 20));

        assertEquals(3, page.getContent().size());
        assertEquals(0, new BigDecimal("300").compareTo(page.getContent().get(0).totalEmissionKg()));
        assertEquals(0, new BigDecimal("100").compareTo(page.getContent().get(2).totalEmissionKg()));
        assertEquals(50.0, page.getContent().get(0).variationPct().doubleValue(), 0.01);
        assertNull(page.getContent().get(2).variationPct());
    }

    @Test
    void listHistoryDeveLimitarTamanhoDaPaginaA20MesmoQuandoSolicitadoMaior() {
        List<SnapshotBucketDTO> buckets = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            buckets.add(month(LocalDate.of(2025, 1, 1).minusMonths(i), String.valueOf(200 - i), 1));
        }
        when(repository.findBucketsMostRecentFirst(any(), any(), any(), anyLong(), anyInt())).thenReturn(buckets);
        when(repository.countBuckets(any(), any(), any())).thenReturn(25L);

        Page<SnapshotAggregateDTO> page = service.listHistory("monthly", null, null, PageRequest.of(0, 50));

        verify(repository).findBucketsMostRecentFirst(SnapshotGranularity.MONTHLY, null, null, 0L, 21);
        assertEquals(20, page.getContent().size());
        assertEquals(20, page.getSize());
        assertEquals(25, page.getTotalElements());
        assertEquals(0, new BigDecimal("181").compareTo(page.getContent().get(19).totalEmissionKg()));
        assertEquals(new BigDecimal("0.56"), page.getContent().get(19).variationPct());
    }

    @Test
    void listHistoryDeveBuscarAPaginaSolicitadaNoBanco() {
        when(repository.findBucketsMostRecentFirst(any(), any(), any(), anyLong(), anyInt())).thenReturn(List.of());
        when(repository.countBuckets(any(), any(), any())).thenReturn(45L);

        service.listHistory("weekly", null, null, PageRequest.of(2, 20));

        verify(repository).findBucketsMostRecentFirst(SnapshotGranularity.WEEKLY, null, null, 40L, 21);
    }
}
