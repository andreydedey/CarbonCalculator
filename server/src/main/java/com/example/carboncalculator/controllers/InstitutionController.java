package com.example.carboncalculator.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.services.InstitutionService;

@RestController
@RequestMapping("/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping
    public List<InstitutionResponse> list() {
        return institutionService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstitutionResponse> getById(@PathVariable UUID id) {
        return institutionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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
