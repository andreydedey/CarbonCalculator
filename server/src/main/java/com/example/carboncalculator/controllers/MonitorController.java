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

import com.example.carboncalculator.dto.CreateMonitorRequest;
import com.example.carboncalculator.dto.MonitorDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.services.MonitorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/monitors")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MonitorDTO> create(@RequestBody CreateMonitorRequest request) {
        MonitorDTO response = monitorService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<MonitorDTO> list(
            @RequestParam(required = false) String name,
            @PageableDefault(sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(monitorService.list(name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<MonitorDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(monitorService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MonitorDTO> update(@PathVariable UUID id,
            @RequestBody CreateMonitorRequest request) {
        return ResponseEntity.ok(monitorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        monitorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
