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

import com.example.carboncalculator.dto.ConsumptionMeasurementDTO;
import com.example.carboncalculator.dto.ConsumptionMeasurementResponse;
import com.example.carboncalculator.dto.CreateConsumptionMeasurementRequest;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.entities.TargetType;
import com.example.carboncalculator.services.ConsumptionMeasurementService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/consumption-measurements")
@RequiredArgsConstructor
public class ConsumptionMeasurementController {

    private final ConsumptionMeasurementService service;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ConsumptionMeasurementResponse> create(
            @Valid @RequestBody CreateConsumptionMeasurementRequest request) {
        ConsumptionMeasurementResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<ConsumptionMeasurementDTO> list(
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) UUID equipmentModelId,
            @RequestParam(required = false) UUID operatingSystemId,
            @RequestParam(required = false) UUID monitorId,
            @PageableDefault(sort = "measurementDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.list(targetType, equipmentModelId, operatingSystemId, monitorId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<ConsumptionMeasurementDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ConsumptionMeasurementResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateConsumptionMeasurementRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
