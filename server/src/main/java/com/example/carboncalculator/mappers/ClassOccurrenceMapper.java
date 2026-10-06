package com.example.carboncalculator.mappers;

import java.time.format.DateTimeFormatter;

import com.example.carboncalculator.dto.ClassOccurrenceDTO;
import com.example.carboncalculator.dto.DayClassesDTO;
import com.example.carboncalculator.entities.ClassOccurrence;
import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.services.ClassSessionExpander;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClassOccurrenceMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** {@code gridStations} is the weekly-grid value for the same class, or null for an extra class. */
    public static ClassOccurrenceDTO toDTO(ClassOccurrence occurrence, Integer gridStations) {
        return new ClassOccurrenceDTO(
                occurrence.getId(),
                occurrence.getLaboratory().getId(),
                occurrence.getLaboratory().getName(),
                occurrence.getShift().getId(),
                occurrence.getShift().getShiftType(),
                occurrence.getDate(),
                occurrence.getSlot(),
                gridStations,
                occurrence.getStationsUsed(),
                ClassSessionStatus.of(gridStations, occurrence.getStationsUsed()));
    }

    public static DayClassesDTO.DayClass toDayClass(ClassSessionExpander.Session session, Laboratory laboratory,
                                                    int capacity) {
        return new DayClassesDTO.DayClass(
                laboratory.getId(),
                laboratory.getName(),
                capacity,
                session.shift().getId(),
                session.shift().getShiftType(),
                session.slot(),
                session.startTime().format(TIME_FMT),
                session.endTime().format(TIME_FMT),
                session.gridStations(),
                session.stationsUsed(),
                session.status());
    }
}
