package com.example.carboncalculator.entities;

public enum SnapshotGranularity {
    DAILY,
    WEEKLY,
    MONTHLY,
    PERIOD;

    public static SnapshotGranularity from(String value) {
        if (value == null) return MONTHLY;
        return switch (value.toLowerCase()) {
            case "daily" -> DAILY;
            case "weekly" -> WEEKLY;
            case "period" -> PERIOD;
            default -> MONTHLY;
        };
    }
}
