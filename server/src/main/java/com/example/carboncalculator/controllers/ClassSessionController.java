package com.example.carboncalculator.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.DayClassesDTO;
import com.example.carboncalculator.services.ClassOccurrenceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController {

    private final ClassOccurrenceService occurrenceService;

    /** Classes of every laboratory on {@code date} (today when omitted). */
    @GetMapping
    @PreAuthorize("hasRole('RESEARCHER')")
    public DayClassesDTO dayClasses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return occurrenceService.dayClasses(date);
    }
}
