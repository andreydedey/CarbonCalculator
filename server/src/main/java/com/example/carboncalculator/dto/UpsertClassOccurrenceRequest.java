package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Registers what happened to one class on a date. {@code stationsUsed = 0} cancels it.
 */
public record UpsertClassOccurrenceRequest(
        UUID shiftId,
        LocalDate date,
        int slot,
        int stationsUsed) {
}
