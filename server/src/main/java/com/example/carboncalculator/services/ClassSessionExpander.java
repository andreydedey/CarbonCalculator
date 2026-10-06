package com.example.carboncalculator.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodHoliday;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.ClassOccurrence;
import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.LaboratorySchedule;

/**
 * Expands the weekly grid plus per-date exceptions into the concrete classes of each
 * school day. This is the single source of "what happened on which day" for the
 * emission calculation and the day view.
 */
public final class ClassSessionExpander {

    /** One class of one laboratory on one date. Cancelled classes have {@code stationsUsed = 0}. */
    public record Session(
            LocalDate date,
            UUID laboratoryId,
            AcademicPeriodShift shift,
            int slot,
            Integer gridStations,
            int stationsUsed,
            ClassSessionStatus status) {

        public double hours() {
            return shift.getClassDurationMinutes() / 60.0;
        }

        public LocalTime startTime() {
            int offset = (slot - 1) * (shift.getClassDurationMinutes() + shift.getBreakDurationMinutes());
            return shift.getStartTime().plusMinutes(offset);
        }

        public LocalTime endTime() {
            return startTime().plusMinutes(shift.getClassDurationMinutes());
        }
    }

    private record GridKey(UUID laboratoryId, UUID shiftId, int dayOfWeek) {}

    private record DayKey(LocalDate date, UUID laboratoryId, UUID shiftId) {}

    private ClassSessionExpander() {
    }

    /**
     * Returns every class between {@code from} and {@code to} (inclusive, clamped to the period),
     * skipping holidays, disabled shifts and days outside each shift's active days.
     */
    public static List<Session> expand(AcademicPeriod period, List<LaboratorySchedule> schedules,
                                       List<ClassOccurrence> occurrences, LocalDate from, LocalDate to) {
        LocalDate start = from.isAfter(period.getStartDate()) ? from : period.getStartDate();
        LocalDate end = to.isBefore(period.getEndDate()) ? to : period.getEndDate();
        if (start.isAfter(end)) {
            return List.of();
        }

        Set<LocalDate> holidays = period.getHolidays().stream()
                .map(AcademicPeriodHoliday::getDate)
                .collect(Collectors.toSet());

        List<AcademicPeriodShift> enabledShifts = period.getShifts().stream()
                .filter(AcademicPeriodShift::isEnabled)
                .sorted(Comparator.comparing(AcademicPeriodShift::getShiftType))
                .toList();

        Map<GridKey, Map<Integer, Integer>> grid = new HashMap<>();
        for (LaboratorySchedule schedule : schedules) {
            Map<Integer, Integer> slots = grid.computeIfAbsent(new GridKey(
                    schedule.getLaboratory().getId(), schedule.getShift().getId(), schedule.getDayOfWeek()),
                    k -> new HashMap<>());
            short[] occupied = schedule.getOccupiedSlots();
            short[] stations = schedule.getStationsUsed();
            for (int i = 0; i < occupied.length; i++) {
                slots.put((int) occupied[i], (int) stations[i]);
            }
        }

        Map<DayKey, Map<Integer, Integer>> overrides = new HashMap<>();
        for (ClassOccurrence occurrence : occurrences) {
            overrides.computeIfAbsent(new DayKey(
                    occurrence.getDate(), occurrence.getLaboratory().getId(), occurrence.getShift().getId()),
                    k -> new HashMap<>())
                    .put((int) occurrence.getSlot(), (int) occurrence.getStationsUsed());
        }

        List<Session> sessions = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (holidays.contains(date)) continue;
            int dayOfWeek = date.getDayOfWeek().getValue();

            for (AcademicPeriodShift shift : enabledShifts) {
                if (!isActiveDay(shift, dayOfWeek)) continue;

                Set<UUID> labs = new LinkedHashSet<>();
                for (GridKey key : grid.keySet()) {
                    if (key.shiftId().equals(shift.getId()) && key.dayOfWeek() == dayOfWeek) labs.add(key.laboratoryId());
                }
                for (DayKey key : overrides.keySet()) {
                    if (key.shiftId().equals(shift.getId()) && key.date().equals(date)) labs.add(key.laboratoryId());
                }

                for (UUID labId : labs.stream().sorted().toList()) {
                    Map<Integer, Integer> gridSlots = grid.getOrDefault(new GridKey(labId, shift.getId(), dayOfWeek), Map.of());
                    Map<Integer, Integer> daySlots = overrides.getOrDefault(new DayKey(date, labId, shift.getId()), Map.of());

                    Set<Integer> allSlots = new TreeSet<>(gridSlots.keySet());
                    allSlots.addAll(daySlots.keySet());

                    for (int slot : allSlots) {
                        if (slot < 1 || slot > shift.getClassesPerDay()) continue;
                        Integer gridStations = gridSlots.get(slot);
                        int stationsUsed = daySlots.containsKey(slot) ? daySlots.get(slot) : gridStations;
                        sessions.add(new Session(date, labId, shift, slot, gridStations, stationsUsed,
                                ClassSessionStatus.of(gridStations, stationsUsed)));
                    }
                }
            }
        }
        return sessions;
    }

    /** Whether {@code date} is a day on which {@code shift} can have classes. */
    public static boolean isSchoolDayForShift(AcademicPeriod period, AcademicPeriodShift shift, LocalDate date) {
        if (date.isBefore(period.getStartDate()) || date.isAfter(period.getEndDate())) return false;
        boolean holiday = period.getHolidays().stream().anyMatch(h -> h.getDate().equals(date));
        return !holiday && shift.isEnabled() && isActiveDay(shift, date.getDayOfWeek().getValue());
    }

    private static boolean isActiveDay(AcademicPeriodShift shift, int dayOfWeek) {
        for (short d : shift.getActiveDays()) {
            if (d == dayOfWeek) return true;
        }
        return false;
    }
}
