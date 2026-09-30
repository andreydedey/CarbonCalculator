package com.example.carboncalculator.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.PeriodSummaryDTO;
import com.example.carboncalculator.dto.PeriodSummaryDTO.LaboratorySummary;
import com.example.carboncalculator.dto.PeriodSummaryDTO.MonthHours;
import com.example.carboncalculator.dto.PeriodSummaryDTO.MonthSchoolDays;
import com.example.carboncalculator.dto.PeriodSummaryDTO.PeriodInfo;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodHoliday;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PeriodSummaryService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AcademicPeriodRepository periodRepository;
    private final LaboratoryScheduleRepository scheduleRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Transactional(readOnly = true)
    public PeriodSummaryDTO getSummary(UUID periodId) {
        AcademicPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new PeriodNotFoundException(periodId));

        Set<LocalDate> holidayDates = period.getHolidays().stream()
                .map(AcademicPeriodHoliday::getDate)
                .collect(Collectors.toSet());

        // Collect all active days across enabled shifts
        Set<DayOfWeek> allActiveDays = period.getShifts().stream()
                .filter(AcademicPeriodShift::isEnabled)
                .flatMap(shift -> {
                    short[] days = shift.getActiveDays();
                    List<DayOfWeek> dowList = new ArrayList<>();
                    for (short d : days) {
                        dowList.add(DayOfWeek.of(d));
                    }
                    return dowList.stream();
                })
                .collect(Collectors.toSet());

        // Calculate school days per month (union of all active days from enabled shifts)
        Map<YearMonth, Integer> schoolDaysByMonth = calculateSchoolDaysPerMonth(
                period.getStartDate(), period.getEndDate(), holidayDates, allActiveDays);

        List<MonthSchoolDays> schoolDaysPerMonth = schoolDaysByMonth.entrySet().stream()
                .map(e -> new MonthSchoolDays(e.getKey().format(MONTH_FORMAT), e.getValue()))
                .toList();

        // Calculate school days per month per day-of-week (for schedule calculation)
        Map<YearMonth, Map<DayOfWeek, Integer>> schoolDaysByMonthAndDow = calculateSchoolDaysByMonthAndDow(
                period.getStartDate(), period.getEndDate(), holidayDates);

        // Index enabled shifts by ID
        Map<UUID, AcademicPeriodShift> enabledShiftsById = period.getShifts().stream()
                .filter(AcademicPeriodShift::isEnabled)
                .collect(Collectors.toMap(AcademicPeriodShift::getId, s -> s));

        // Get all schedules for this period
        List<LaboratorySchedule> allSchedules = scheduleRepository.findByPeriodId(periodId);

        // Filter to only schedules whose shift is enabled
        Map<UUID, List<LaboratorySchedule>> schedulesByLab = allSchedules.stream()
                .filter(s -> enabledShiftsById.containsKey(s.getShift().getId()))
                .collect(Collectors.groupingBy(s -> s.getLaboratory().getId()));

        // Get all labs in the institution
        List<Laboratory> allLabs = laboratoryRepository.findAll();

        List<LaboratorySummary> labSummaries = new ArrayList<>();
        for (Laboratory lab : allLabs) {
            List<LaboratorySchedule> labSchedules = schedulesByLab.getOrDefault(lab.getId(), List.of());
            List<MonthHours> hoursPerMonth = calculateHoursPerMonth(labSchedules, enabledShiftsById, schoolDaysByMonthAndDow);
            double totalHours = hoursPerMonth.stream().mapToDouble(MonthHours::hours).sum();
            labSummaries.add(new LaboratorySummary(lab.getId(), lab.getName(), hoursPerMonth, totalHours));
        }

        PeriodInfo periodInfo = new PeriodInfo(
                period.getId(), period.getName(), period.getStartDate(), period.getEndDate());

        return new PeriodSummaryDTO(periodInfo, schoolDaysPerMonth, labSummaries);
    }

    private Map<YearMonth, Integer> calculateSchoolDaysPerMonth(
            LocalDate start, LocalDate end, Set<LocalDate> holidays, Set<DayOfWeek> activeDays) {
        Map<YearMonth, Integer> result = new LinkedHashMap<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            if (isSchoolDay(current, holidays, activeDays)) {
                YearMonth ym = YearMonth.from(current);
                result.merge(ym, 1, Integer::sum);
            }
            current = current.plusDays(1);
        }

        return result;
    }

    private Map<YearMonth, Map<DayOfWeek, Integer>> calculateSchoolDaysByMonthAndDow(
            LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        Map<YearMonth, Map<DayOfWeek, Integer>> result = new LinkedHashMap<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            // Sundays are never school days
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(current)) {
                YearMonth ym = YearMonth.from(current);
                result.computeIfAbsent(ym, k -> new LinkedHashMap<>())
                        .merge(current.getDayOfWeek(), 1, Integer::sum);
            }
            current = current.plusDays(1);
        }

        return result;
    }

    private boolean isSchoolDay(LocalDate date, Set<LocalDate> holidays, Set<DayOfWeek> activeDays) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (holidays.contains(date)) {
            return false;
        }
        return activeDays.contains(date.getDayOfWeek());
    }

    private List<MonthHours> calculateHoursPerMonth(
            List<LaboratorySchedule> schedules,
            Map<UUID, AcademicPeriodShift> enabledShiftsById,
            Map<YearMonth, Map<DayOfWeek, Integer>> schoolDaysByMonthAndDow) {

        List<MonthHours> result = new ArrayList<>();

        for (var monthEntry : schoolDaysByMonthAndDow.entrySet()) {
            YearMonth month = monthEntry.getKey();
            Map<DayOfWeek, Integer> dowCounts = monthEntry.getValue();

            double monthHours = 0;
            for (LaboratorySchedule schedule : schedules) {
                AcademicPeriodShift shift = enabledShiftsById.get(schedule.getShift().getId());
                if (shift == null) continue;

                DayOfWeek dow = DayOfWeek.of(schedule.getDayOfWeek());
                int daysInMonth = dowCounts.getOrDefault(dow, 0);

                // hours = len(occupiedSlots) × classDurationMinutes × daysInMonth / 60
                int slotCount = schedule.getOccupiedSlots() != null ? schedule.getOccupiedSlots().length : 0;
                double hours = slotCount * shift.getClassDurationMinutes() * daysInMonth / 60.0;
                monthHours += hours;
            }

            result.add(new MonthHours(month.format(MONTH_FORMAT), Math.round(monthHours * 10) / 10.0));
        }

        return result;
    }
}
