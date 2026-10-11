package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SnapshotFilter(LocalDate startDate, LocalDate endDate, UUID laboratoryId) {

    public static SnapshotFilter none() {
        return new SnapshotFilter(null, null, null);
    }

    public boolean hasDateRange() {
        return startDate != null && endDate != null;
    }
}
