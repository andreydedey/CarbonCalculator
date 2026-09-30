package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

public record ReplaceScheduleRequest(List<ScheduleInput> entries) {

    public record ScheduleInput(
            UUID shiftId,
            int dayOfWeek,
            List<Integer> occupiedSlots) {
    }
}
