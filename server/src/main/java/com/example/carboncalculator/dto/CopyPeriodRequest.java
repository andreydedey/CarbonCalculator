package com.example.carboncalculator.dto;

import java.time.LocalDate;

public record CopyPeriodRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate) {
}
