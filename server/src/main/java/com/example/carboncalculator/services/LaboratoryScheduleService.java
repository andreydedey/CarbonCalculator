package com.example.carboncalculator.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ScheduleEntryDTO;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.exceptions.LaboratoryNotFoundException;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.exceptions.ShiftValidationException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.AcademicPeriodShiftRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaboratoryScheduleService {

    private static final Logger log = LoggerFactory.getLogger(LaboratoryScheduleService.class);

    private final LaboratoryScheduleRepository scheduleRepository;
    private final AcademicPeriodRepository periodRepository;
    private final AcademicPeriodShiftRepository shiftRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryEquipmentRepository labEquipmentRepository;

    @Transactional(readOnly = true)
    public List<ScheduleEntryDTO> getSchedule(UUID periodId, UUID laboratoryId) {
        if (!periodRepository.existsById(periodId)) {
            throw new PeriodNotFoundException(periodId);
        }
        return scheduleRepository.findByPeriodIdAndLaboratoryId(periodId, laboratoryId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<ScheduleEntryDTO> replaceSchedule(UUID periodId, UUID laboratoryId, ReplaceScheduleRequest request) {
        if (!periodRepository.existsById(periodId)) {
            throw new PeriodNotFoundException(periodId);
        }
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new LaboratoryNotFoundException(laboratoryId));

        // Load all shifts for this period, indexed by ID
        Map<UUID, AcademicPeriodShift> shiftsById = shiftRepository.findByAcademicPeriodId(periodId)
                .stream()
                .collect(Collectors.toMap(AcademicPeriodShift::getId, Function.identity()));

        int capacity = labEquipmentRepository.sumQuantityByLaboratoryId(laboratoryId);
        validateEntries(request.entries(), shiftsById, capacity);

        // Delete old schedule for this lab in this period
        List<LaboratorySchedule> existing = scheduleRepository.findByPeriodIdAndLaboratoryId(periodId, laboratoryId);
        scheduleRepository.deleteAll(existing);
        scheduleRepository.flush();

        List<LaboratorySchedule> newSchedules = request.entries().stream()
                .map(entry -> LaboratorySchedule.builder()
                        .shift(shiftsById.get(entry.shiftId()))
                        .laboratory(laboratory)
                        .dayOfWeek((short) entry.dayOfWeek())
                        .occupiedSlots(toShortArray(entry.occupiedSlots()))
                        .stationsUsed(resolveStations(entry, capacity))
                        .build())
                .toList();

        scheduleRepository.saveAll(newSchedules);
        log.info("Schedule replaced for period={}, lab={}: {} entries", periodId, laboratoryId, request.entries().size());

        return newSchedules.stream().map(this::toDTO).toList();
    }

    private void validateEntries(List<ReplaceScheduleRequest.ScheduleInput> entries,
                                 Map<UUID, AcademicPeriodShift> shiftsById, int capacity) {
        for (ReplaceScheduleRequest.ScheduleInput entry : entries) {
            AcademicPeriodShift shift = shiftsById.get(entry.shiftId());
            if (shift == null) {
                throw new ShiftValidationException("Turno não encontrado: " + entry.shiftId());
            }

            // Validate dayOfWeek is within the shift's active days
            short[] activeDays = shift.getActiveDays();
            boolean dayAllowed = false;
            for (short d : activeDays) {
                if (d == entry.dayOfWeek()) {
                    dayAllowed = true;
                    break;
                }
            }
            if (!dayAllowed) {
                throw new ShiftValidationException(
                        "Dia " + entry.dayOfWeek() + " não está nos dias ativos do turno " + shift.getShiftType());
            }

            // Validate each slot is within [1, classesPerDay] and appears once
            Set<Integer> seen = new HashSet<>();
            for (int slot : entry.occupiedSlots()) {
                if (slot < 1 || slot > shift.getClassesPerDay()) {
                    throw new ShiftValidationException(
                            "Slot " + slot + " fora do range [1, " + shift.getClassesPerDay() + "] para o turno " + shift.getShiftType());
                }
                if (!seen.add(slot)) {
                    throw new ShiftValidationException("Slot " + slot + " repetido no dia " + entry.dayOfWeek());
                }
            }

            validateStations(entry, capacity);
        }
    }

    private void validateStations(ReplaceScheduleRequest.ScheduleInput entry, int capacity) {
        if (entry.stationsUsed() == null) {
            return;
        }
        if (entry.stationsUsed().size() != entry.occupiedSlots().size()) {
            throw new ShiftValidationException(
                    "Informe o número de estações usadas para cada aula ocupada do dia " + entry.dayOfWeek());
        }
        for (Integer stations : entry.stationsUsed()) {
            if (stations == null || stations < 1) {
                throw new ShiftValidationException("Cada aula ocupada deve usar ao menos 1 estação");
            }
            if (capacity > 0 && stations > capacity) {
                throw new ShiftValidationException(
                        "O laboratório tem " + capacity + " estações; não é possível usar " + stations);
            }
        }
    }

    // Omitted stations default to the full laboratory, matching the original assumption
    private short[] resolveStations(ReplaceScheduleRequest.ScheduleInput entry, int capacity) {
        if (entry.stationsUsed() != null) {
            return toShortArray(entry.stationsUsed());
        }
        short[] stations = new short[entry.occupiedSlots().size()];
        Arrays.fill(stations, (short) Math.max(1, capacity));
        return stations;
    }

    private ScheduleEntryDTO toDTO(LaboratorySchedule schedule) {
        return new ScheduleEntryDTO(
                schedule.getShift().getId(),
                schedule.getShift().getShiftType(),
                schedule.getDayOfWeek(),
                toIntList(schedule.getOccupiedSlots()),
                toIntList(schedule.getStationsUsed()));
    }

    private List<Integer> toIntList(short[] arr) {
        if (arr == null) return List.of();
        List<Integer> result = new ArrayList<>(arr.length);
        for (short s : arr) {
            result.add((int) s);
        }
        return result;
    }

    private short[] toShortArray(List<Integer> list) {
        short[] arr = new short[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).shortValue();
        }
        return arr;
    }
}
