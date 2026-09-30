package com.example.carboncalculator.dto;

import java.time.LocalDate;

public record CreateAcademicPeriodRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate) {
}
