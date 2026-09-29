package com.example.carboncalculator.dto;

import java.time.LocalDate;

import com.example.carboncalculator.entities.HolidayType;

public record HolidayDTO(
        LocalDate date,
        String description,
        HolidayType type) {
}
