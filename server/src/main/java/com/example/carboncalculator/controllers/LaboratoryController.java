package com.example.carboncalculator.controllers;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.services.LaboratoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/laboratories")
@RequiredArgsConstructor
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<LaboratoryDTO> create(@RequestBody CreateLaboratoryRequest request) {
        LaboratoryDTO response = laboratoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<LaboratoryDTO> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String name,
            Pageable pageable) {
        return PageResponse.from(laboratoryService.list(active, name, pageable));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<LaboratoryDTO> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(laboratoryService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<LaboratoryDTO> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(laboratoryService.deactivate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        laboratoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
