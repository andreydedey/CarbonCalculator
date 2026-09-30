package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

import com.example.carboncalculator.entities.ShiftType;

public record ScheduleEntryDTO(
        UUID shiftId,
        ShiftType shiftType,
        int dayOfWeek,
        List<Integer> occupiedSlots) {
}
