package com.example.carboncalculator.controllers;

import java.util.UUID;

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
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.LaboratoryCompositionDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.services.LaboratoryEquipmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/laboratories/{labId}/equipment")
@RequiredArgsConstructor
public class LaboratoryEquipmentController {

    private final LaboratoryEquipmentService laboratoryEquipmentService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<LaboratoryEquipmentDTO> create(
            @PathVariable UUID labId,
            @RequestBody CreateLaboratoryEquipmentRequest request) {
        LaboratoryEquipmentDTO response = laboratoryEquipmentService.create(labId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public LaboratoryCompositionDTO getComposition(@PathVariable UUID labId) {
        return laboratoryEquipmentService.getComposition(labId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<LaboratoryEquipmentDTO> update(
            @PathVariable UUID labId,
            @PathVariable UUID id,
            @RequestBody CreateLaboratoryEquipmentRequest request) {
        return ResponseEntity.ok(laboratoryEquipmentService.update(labId, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID labId, @PathVariable UUID id) {
        laboratoryEquipmentService.delete(labId, id);
        return ResponseEntity.noContent().build();
    }
}
