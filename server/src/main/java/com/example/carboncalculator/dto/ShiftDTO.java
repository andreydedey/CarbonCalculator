package com.example.carboncalculator.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.example.carboncalculator.entities.ShiftType;

public record ShiftDTO(
        UUID id,
        ShiftType shiftType,
        LocalTime startTime,
        LocalTime endTime,
        int classesPerDay,
        int classDurationMinutes,
        int breakDurationMinutes,
        List<Integer> activeDays,
        boolean enabled) {
}
