package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AcademicPeriodDTO(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int holidayCount,
        OffsetDateTime createdAt) {
}
