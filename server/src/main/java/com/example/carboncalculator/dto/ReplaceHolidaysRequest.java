package com.example.carboncalculator.dto;

import java.util.List;

public record ReplaceHolidaysRequest(
        List<HolidayDTO> holidays) {
}
