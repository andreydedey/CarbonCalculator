package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.exceptions.DuplicateEmissionFactorException;
import com.example.carboncalculator.exceptions.EmissionFactorNotFoundException;
import com.example.carboncalculator.exceptions.InvalidEmissionFactorException;
import com.example.carboncalculator.repositories.EmissionFactorRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;

class EmissionFactorServiceTest {

    private final EmissionFactorRepository repository = mock(EmissionFactorRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);

    private EmissionFactorService service;

    @BeforeEach
    void setUp() {
        service = new EmissionFactorService(repository, institutionRepository);
    }

    // @spec:AC-104
    @Test
    void deveRecusarFatorSemMesDeReferencia() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                null, BigDecimal.valueOf(0.1234), "MCTI");

        assertThrows(InvalidEmissionFactorException.class, () -> service.create(request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-105
    @Test
    void deveRecusarFatorComValorZero() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), BigDecimal.ZERO, "MCTI");

        assertThrows(InvalidEmissionFactorException.class, () -> service.create(request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-105
    @Test
    void deveRecusarFatorComValorNegativo() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), BigDecimal.valueOf(-0.1), "MCTI");

        assertThrows(InvalidEmissionFactorException.class, () -> service.create(request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-106
    @Test
    void deveRecusarFatorSemFonte() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), BigDecimal.valueOf(0.1234), "   ");

        assertThrows(InvalidEmissionFactorException.class, () -> service.create(request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-107
    @Test
    void deveRecusarCriacaoDuplicadaDoMesmoMes() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), BigDecimal.valueOf(0.1234), "MCTI");

        when(repository.existsByReferenceMonth(YearMonth.of(2025, 6))).thenReturn(true);

        assertThrows(DuplicateEmissionFactorException.class, () -> service.create(request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-109
    @Test
    void deveRecusarAtualizacaoParaMesJaOcupadoPorOutroFator() {
        UUID id = UUID.randomUUID();
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), BigDecimal.valueOf(0.1234), "MCTI");

        EmissionFactor existing = EmissionFactor.builder()
                .id(id)
                .referenceMonth(YearMonth.of(2025, 5))
                .value(BigDecimal.valueOf(0.1111))
                .source("MCTI")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByReferenceMonthAndIdNot(YearMonth.of(2025, 6), id)).thenReturn(true);

        assertThrows(DuplicateEmissionFactorException.class, () -> service.update(id, request));

        verify(repository, never()).save(any(EmissionFactor.class));
    }

    // @spec:AC-111
    @Test
    void deveRetornarNotFoundAoRemoverFatorInexistente() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EmissionFactorNotFoundException.class, () -> service.delete(id));

        verify(repository, never()).delete(any(EmissionFactor.class));
    }
}
