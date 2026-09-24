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

import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.services.EquipmentModelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/equipment-models")
@RequiredArgsConstructor
public class EquipmentModelController {

    private final EquipmentModelService equipmentModelService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<EquipmentModelDTO> create(@RequestBody CreateEquipmentModelRequest request) {
        EquipmentModelDTO response = equipmentModelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<EquipmentModelDTO> list(
            @RequestParam(required = false) String name,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(equipmentModelService.list(name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<EquipmentModelDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(equipmentModelService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<EquipmentModelDTO> update(@PathVariable UUID id,
            @RequestBody CreateEquipmentModelRequest request) {
        return ResponseEntity.ok(equipmentModelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        equipmentModelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
