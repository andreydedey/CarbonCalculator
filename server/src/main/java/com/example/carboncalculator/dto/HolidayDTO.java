package com.example.carboncalculator.dto;

import java.time.LocalDate;

public record HolidayDTO(
        LocalDate date,
        String description) {
}
