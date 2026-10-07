package com.example.carboncalculator.dto;

import java.util.UUID;

public record MonitorDTO(
        UUID id,
        String name,
        Integer watts) {
}
