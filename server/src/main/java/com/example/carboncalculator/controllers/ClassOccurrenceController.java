package com.example.carboncalculator.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.ClassOccurrenceDTO;
import com.example.carboncalculator.dto.UpsertClassOccurrenceRequest;
import com.example.carboncalculator.services.ClassOccurrenceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/academic-periods/{periodId}")
@RequiredArgsConstructor
public class ClassOccurrenceController {

    private final ClassOccurrenceService occurrenceService;

    @GetMapping("/occurrences")
    @PreAuthorize("hasRole('RESEARCHER')")
    public List<ClassOccurrenceDTO> list(
            @PathVariable UUID periodId,
            @RequestParam(required = false) UUID laboratoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return occurrenceService.list(periodId, laboratoryId, from, to);
    }

    /** 200 with the exception, or 204 when the value matches the weekly grid and nothing is stored. */
    @PutMapping("/laboratories/{labId}/occurrences")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ClassOccurrenceDTO> upsert(
            @PathVariable UUID periodId, @PathVariable UUID labId,
            @RequestBody UpsertClassOccurrenceRequest request) {
        return occurrenceService.upsert(periodId, labId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/laboratories/{labId}/occurrences")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID periodId, @PathVariable UUID labId,
            @RequestParam UUID shiftId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam int slot) {
        occurrenceService.delete(periodId, labId, shiftId, date, slot);
        return ResponseEntity.noContent().build();
    }
}
