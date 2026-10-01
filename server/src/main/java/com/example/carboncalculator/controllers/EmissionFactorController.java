package com.example.carboncalculator.controllers;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.services.EmissionFactorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/emission-factors")
@RequiredArgsConstructor
public class EmissionFactorController {

    private final EmissionFactorService emissionFactorService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmissionFactorDTO> create(@RequestBody CreateEmissionFactorRequest request) {
        EmissionFactorDTO response = emissionFactorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PageResponse<EmissionFactorDTO> list(
            @RequestParam(required = false) Integer year,
            @PageableDefault(sort = "referenceMonth", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(emissionFactorService.list(year, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmissionFactorDTO> update(@PathVariable UUID id,
            @RequestBody CreateEmissionFactorRequest request) {
        return ResponseEntity.ok(emissionFactorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        emissionFactorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
