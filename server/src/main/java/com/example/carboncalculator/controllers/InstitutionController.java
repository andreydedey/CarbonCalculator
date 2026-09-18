package com.example.carboncalculator.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.services.InstitutionService;

/**
 * Endpoints REST de instituição (US-001). Não exige o header
 * {@code X-Institution-Id} — a tabela {@code institution} não tem RLS (ver
 * TenantFilter e ADR-004), conforme seção "API REST" do TDD.
 */
@RestController
@RequestMapping("/api/v1/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    // @spec:AC-001 Instituição criada com dados válidos
    @PostMapping
    public ResponseEntity<InstitutionResponse> create(@RequestBody CreateInstitutionRequest request) {
        InstitutionResponse response = institutionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(InstitutionService.DuplicateAcronymException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateAcronym(InstitutionService.DuplicateAcronymException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InstitutionService.InvalidStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(InstitutionService.InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
