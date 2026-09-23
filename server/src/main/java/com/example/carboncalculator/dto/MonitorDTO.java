package com.example.carboncalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MonitorDTO(
        UUID id,
        String name,
        Integer watts,
        OffsetDateTime createdAt) {
}
