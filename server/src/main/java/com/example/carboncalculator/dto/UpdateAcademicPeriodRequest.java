package com.example.carboncalculator.dto;

import java.time.LocalDate;

public record UpdateAcademicPeriodRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate) {
}
