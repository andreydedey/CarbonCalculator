package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

import com.example.carboncalculator.entities.ShiftType;

/**
 * One weekday of a laboratory's weekly grid in a shift. {@code stationsUsed}
 * is aligned by index with {@code occupiedSlots}.
 */
public record ScheduleEntryDTO(
        UUID shiftId,
        ShiftType shiftType,
        int dayOfWeek,
        List<Integer> occupiedSlots,
        List<Integer> stationsUsed) {
}
