package com.example.carboncalculator.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.entities.TargetType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateConsumptionMeasurementRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final UUID MODEL = UUID.randomUUID();
    private static final UUID OS = UUID.randomUUID();
    private static final UUID MONITOR = UUID.randomUUID();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CreateConsumptionMeasurementRequest request(TargetType type, UUID model, UUID os, UUID monitor) {
        return new CreateConsumptionMeasurementRequest(type, model, os, monitor,
                new BigDecimal("45.5"), 30, 1, LocalDate.of(2026, 9, 1), null, null);
    }

    private static Set<String> violations(CreateConsumptionMeasurementRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void deveAceitarCadaTipoDeAlvoComAsPartesCorretas() {
        assertTrue(violations(request(TargetType.COMPUTER, MODEL, OS, null)).isEmpty());
        assertTrue(violations(request(TargetType.MONITOR, null, null, MONITOR)).isEmpty());
        assertTrue(violations(request(TargetType.COMBINED, MODEL, OS, MONITOR)).isEmpty());
    }

    @Test
    void deveRejeitarPartesFaltantesOuSobrandoParaOTipoDeAlvo() {
        assertEquals(1, violations(request(TargetType.COMPUTER, MODEL, OS, MONITOR)).size());
        assertEquals(1, violations(request(TargetType.COMPUTER, null, OS, null)).size());
        assertEquals(2, violations(request(TargetType.MONITOR, MODEL, OS, MONITOR)).size());
        assertEquals(1, violations(request(TargetType.COMBINED, MODEL, OS, null)).size());
    }

    @Test
    void deveRejeitarCamposObrigatoriosAusentesOuNaoPositivos() {
        CreateConsumptionMeasurementRequest invalid = new CreateConsumptionMeasurementRequest(
                null, null, null, null, BigDecimal.ZERO, 0, null, null, null, null);

        Set<String> messages = violations(invalid);
        assertTrue(messages.contains("Tipo de alvo é obrigatório"));
        assertTrue(messages.contains("Consumo médio deve ser maior que zero"));
        assertTrue(messages.contains("Duração deve ser maior que zero"));
        assertTrue(messages.contains("Data da medição é obrigatória"));
    }
}
