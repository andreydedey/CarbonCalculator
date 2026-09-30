package com.example.carboncalculator.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PeriodSummaryDTO(
        PeriodInfo period,
        List<MonthSchoolDays> schoolDaysPerMonth,
        List<LaboratorySummary> laboratorySummaries) {

    public record PeriodInfo(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record MonthSchoolDays(
            String month,
            int schoolDays) {
    }

    public record LaboratorySummary(
            UUID laboratoryId,
            String laboratoryName,
            List<MonthHours> hoursPerMonth,
            double totalHours) {
    }

    public record MonthHours(
            String month,
            double hours) {
    }
}
