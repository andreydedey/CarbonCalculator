package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.ShiftType;

/**
 * Every class of the institution on one date, with the grid value and what was registered.
 * {@code periodId} is null when the date is outside every academic period.
 */
public record DayClassesDTO(
        LocalDate date,
        UUID periodId,
        String periodName,
        boolean schoolDay,
        String holidayName,
        List<DayClass> classes) {

    public record DayClass(
            UUID laboratoryId,
            String laboratoryName,
            int capacity,
            UUID shiftId,
            ShiftType shiftType,
            int slot,
            String startTime,
            String endTime,
            Integer gridStations,
            int stationsUsed,
            ClassSessionStatus status) {
    }
}
