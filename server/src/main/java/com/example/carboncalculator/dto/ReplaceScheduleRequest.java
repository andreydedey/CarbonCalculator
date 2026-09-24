package com.example.carboncalculator.dto;

import java.util.List;

public record ReplaceScheduleRequest(
        List<ScheduleBlockDTO> blocks) {
}
