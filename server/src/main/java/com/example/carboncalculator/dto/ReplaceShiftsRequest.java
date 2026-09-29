package com.example.carboncalculator.dto;

import java.time.LocalTime;
import java.util.List;

import com.example.carboncalculator.entities.ShiftType;

public record ReplaceShiftsRequest(List<ShiftInput> shifts) {

    public record ShiftInput(
            ShiftType shiftType,
            LocalTime startTime,
            int classesPerDay,
            int classDurationMinutes,
            int breakDurationMinutes,
            List<Integer> activeDays,
            boolean enabled) {
    }
}
