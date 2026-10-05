package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.ShiftType;

/**
 * A registered exception. {@code gridStations} is null for an extra class.
 */
public record ClassOccurrenceDTO(
        UUID id,
        UUID laboratoryId,
        String laboratoryName,
        UUID shiftId,
        ShiftType shiftType,
        LocalDate date,
        int slot,
        Integer gridStations,
        int stationsUsed,
        ClassSessionStatus status) {
}
