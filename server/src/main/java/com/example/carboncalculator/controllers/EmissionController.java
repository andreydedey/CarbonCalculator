package com.example.carboncalculator.controllers;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.EmissionResultDTO;
import com.example.carboncalculator.dto.ReadinessDTO;
import com.example.carboncalculator.services.EmissionCalculationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/academic-periods/{periodId}/emissions")
@RequiredArgsConstructor
public class EmissionController {

    private final EmissionCalculationService calculationService;

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public EmissionResultDTO calculate(@PathVariable UUID periodId) {
        return calculationService.calculate(periodId);
    }

    @GetMapping("/readiness")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ReadinessDTO checkReadiness(@PathVariable UUID periodId) {
        return calculationService.checkReadiness(periodId);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID periodId) {
        String csv = calculationService.exportCsv(periodId);
        byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"emissoes.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(body.length)
                .body(body);
    }
}
