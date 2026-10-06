package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

public record ReplaceScheduleRequest(List<ScheduleInput> entries) {

    /**
     * {@code stationsUsed} is aligned by index with {@code occupiedSlots}.
     * When omitted, every occupied slot uses all stations of the laboratory.
     */
    public record ScheduleInput(
            UUID shiftId,
            int dayOfWeek,
            List<Integer> occupiedSlots,
            List<Integer> stationsUsed) {

        public ScheduleInput(UUID shiftId, int dayOfWeek, List<Integer> occupiedSlots) {
            this(shiftId, dayOfWeek, occupiedSlots, null);
        }
    }
}
