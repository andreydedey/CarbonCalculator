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

import com.example.carboncalculator.dto.CreateOperatingSystemRequest;
import com.example.carboncalculator.dto.OperatingSystemDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.services.OperatingSystemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/operating-systems")
@RequiredArgsConstructor
public class OperatingSystemController {

    private final OperatingSystemService operatingSystemService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<OperatingSystemDTO> create(@RequestBody CreateOperatingSystemRequest request) {
        OperatingSystemDTO response = operatingSystemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<OperatingSystemDTO> list(
            @RequestParam(required = false) String name,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(operatingSystemService.list(name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<OperatingSystemDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(operatingSystemService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<OperatingSystemDTO> update(@PathVariable UUID id,
            @RequestBody CreateOperatingSystemRequest request) {
        return ResponseEntity.ok(operatingSystemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        operatingSystemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
