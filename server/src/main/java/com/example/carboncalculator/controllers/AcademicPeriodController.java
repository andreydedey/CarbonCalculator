package com.example.carboncalculator.controllers;

import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.CopyPeriodRequest;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.HolidayDTO;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.dto.PeriodSummaryDTO;
import com.example.carboncalculator.dto.ReplaceHolidaysRequest;
import com.example.carboncalculator.dto.UpdateAcademicPeriodRequest;
import com.example.carboncalculator.services.AcademicPeriodService;
import com.example.carboncalculator.services.PeriodSummaryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/academic-periods")
@RequiredArgsConstructor
public class AcademicPeriodController {

    private final AcademicPeriodService periodService;
    private final PeriodSummaryService summaryService;

    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public PageResponse<AcademicPeriodDTO> list(
            @PageableDefault(sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(periodService.list(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AcademicPeriodDTO> create(@RequestBody CreateAcademicPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<AcademicPeriodDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(periodService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AcademicPeriodDTO> update(
            @PathVariable UUID id, @RequestBody UpdateAcademicPeriodRequest request) {
        return ResponseEntity.ok(periodService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        periodService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/holidays")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<HolidayDTO>> replaceHolidays(
            @PathVariable UUID id, @RequestBody ReplaceHolidaysRequest request) {
        return ResponseEntity.ok(periodService.replaceHolidays(id, request));
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AcademicPeriodDTO> copyPeriod(
            @PathVariable UUID id, @RequestBody CopyPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodService.copyPeriod(id, request));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasRole('RESEARCHER')")
    public ResponseEntity<PeriodSummaryDTO> getSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(summaryService.getSummary(id));
    }
}
