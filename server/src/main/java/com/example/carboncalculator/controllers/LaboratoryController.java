package com.example.carboncalculator.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.services.LaboratoryService;

@RestController
@RequestMapping("/laboratories")
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    public LaboratoryController(LaboratoryService laboratoryService) {
        this.laboratoryService = laboratoryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<LaboratoryDTO> create(@RequestBody CreateLaboratoryRequest request) {
        LaboratoryDTO response = laboratoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('PESQUISADOR')")
    public ResponseEntity<List<LaboratoryDTO>> list(
            @RequestParam(name = "active", required = false, defaultValue = "true") boolean active) {
        boolean includeInactive = !active;
        return ResponseEntity.ok(laboratoryService.list(includeInactive));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<LaboratoryDTO> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(laboratoryService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<LaboratoryDTO> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(laboratoryService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        laboratoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LaboratoryService.MissingLaboratoryNameException.class)
    public ResponseEntity<Map<String, String>> handleMissingName(LaboratoryService.MissingLaboratoryNameException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(LaboratoryService.LaboratoryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(LaboratoryService.LaboratoryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(LaboratoryService.LaboratoryHasDependentsException.class)
    public ResponseEntity<Map<String, String>> handleHasDependents(LaboratoryService.LaboratoryHasDependentsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }
}
