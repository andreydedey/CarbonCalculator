package com.example.carboncalculator.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ScheduleBlockDTO;
import com.example.carboncalculator.services.LaboratoryScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/academic-periods/{periodId}/laboratories/{labId}/schedule")
@RequiredArgsConstructor
public class LaboratoryScheduleController {

    private final LaboratoryScheduleService scheduleService;

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<List<ScheduleBlockDTO>> getSchedule(
            @PathVariable UUID periodId, @PathVariable UUID labId) {
        return ResponseEntity.ok(scheduleService.getSchedule(periodId, labId));
    }

    @PutMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<ScheduleBlockDTO>> replaceSchedule(
            @PathVariable UUID periodId, @PathVariable UUID labId,
            @RequestBody ReplaceScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.replaceSchedule(periodId, labId, request));
    }
}
