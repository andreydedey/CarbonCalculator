package com.example.carboncalculator.dto;

import java.time.LocalTime;

public record ScheduleBlockDTO(
        int dayOfWeek,
        LocalTime startTime,
        LocalTime endTime) {
}
