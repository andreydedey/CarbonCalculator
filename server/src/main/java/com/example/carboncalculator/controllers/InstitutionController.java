package com.example.carboncalculator.controllers;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.exceptions.DuplicateAcronymException;
import com.example.carboncalculator.exceptions.InvalidStateException;
import com.example.carboncalculator.services.InstitutionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionService institutionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<InstitutionDTO> list(@AuthenticationPrincipal AppUser user, Pageable pageable) {
        return PageResponse.from(institutionService.listForUser(user, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InstitutionDTO> getById(@PathVariable UUID id) {
        return institutionService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstitutionDTO> create(@RequestBody CreateInstitutionRequest request) {
        InstitutionDTO response = institutionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(DuplicateAcronymException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateAcronym(DuplicateAcronymException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(InvalidStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
