package com.example.carboncalculator.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.services.EmissionSnapshotQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only longitudinal history of emissions (US-033). Snapshots are
 * captured daily by EmissionSnapshotCronService (US-034); this endpoint
 * aggregates them by the requested granularity. Open to every authenticated
 * role (no restriction) per TDD-08.
 */
@RestController
@RequestMapping("/snapshots")
@RequiredArgsConstructor
public class EmissionSnapshotController {

    private final EmissionSnapshotQueryService queryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SnapshotAggregateDTO> list(
            @RequestParam(defaultValue = "monthly") String granularity,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return queryService.list(granularity, startDate, endDate);
    }

    // Paginated, most-recent-first listing for the "Histórico de Emissões" table.
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<SnapshotAggregateDTO> history(
            @RequestParam(defaultValue = "monthly") String granularity,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(queryService.listHistory(granularity, startDate, endDate, pageable));
    }
}
