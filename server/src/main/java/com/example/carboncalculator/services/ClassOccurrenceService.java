package com.example.carboncalculator.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.ClassOccurrenceDTO;
import com.example.carboncalculator.dto.DayClassesDTO;
import com.example.carboncalculator.dto.UpsertClassOccurrenceRequest;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodHoliday;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.ClassOccurrence;
import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.exceptions.LaboratoryNotFoundException;
import com.example.carboncalculator.exceptions.OccurrenceValidationException;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.ClassOccurrenceRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassOccurrenceService {

    private static final Logger log = LoggerFactory.getLogger(ClassOccurrenceService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final AcademicPeriodRepository periodRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryEquipmentRepository labEquipmentRepository;
    private final LaboratoryScheduleRepository scheduleRepository;
    private final ClassOccurrenceRepository occurrenceRepository;
    private final InstitutionRepository institutionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ClassOccurrenceDTO> list(UUID periodId, UUID laboratoryId, LocalDate from, LocalDate to) {
        AcademicPeriod period = getPeriod(periodId);
        LocalDate start = from != null ? from : period.getStartDate();
        LocalDate end = to != null ? to : period.getEndDate();

        Map<String, Integer> grid = gridStations(scheduleRepository.findByPeriodId(periodId));

        return occurrenceRepository.findByPeriodIdAndDateBetween(periodId, start, end).stream()
                .filter(o -> laboratoryId == null || o.getLaboratory().getId().equals(laboratoryId))
                .sorted(Comparator.comparing(ClassOccurrence::getDate).reversed()
                        .thenComparing(o -> o.getShift().getShiftType())
                        .thenComparing(ClassOccurrence::getSlot))
                .map(o -> toDTO(o, grid.get(gridKey(o.getLaboratory().getId(), o.getShift().getId(),
                        o.getDate().getDayOfWeek().getValue(), o.getSlot()))))
                .toList();
    }

    /**
     * Registers what happened to a class on a date. Returns empty when the value matches the
     * weekly grid, in which case any previous exception is removed.
     */
    @Transactional
    public Optional<ClassOccurrenceDTO> upsert(UUID periodId, UUID laboratoryId, UpsertClassOccurrenceRequest request) {
        AcademicPeriod period = getPeriod(periodId);
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new LaboratoryNotFoundException(laboratoryId));
        AcademicPeriodShift shift = findShift(period, request.shiftId());
        validate(period, shift, request, labEquipmentRepository.sumQuantityByLaboratoryId(laboratoryId));

        Integer gridStations = gridStations(scheduleRepository.findByPeriodIdAndLaboratoryId(periodId, laboratoryId))
                .get(gridKey(laboratoryId, shift.getId(), request.date().getDayOfWeek().getValue(), request.slot()));

        Optional<ClassOccurrence> existing = occurrenceRepository.findByShiftIdAndLaboratoryIdAndDateAndSlot(
                shift.getId(), laboratoryId, request.date(), (short) request.slot());

        boolean matchesGrid = gridStations == null
                ? request.stationsUsed() == 0
                : gridStations == request.stationsUsed();
        if (matchesGrid) {
            existing.ifPresent(occurrenceRepository::delete);
            log.info("Occurrence back to grid: lab={}, shift={}, date={}, slot={}",
                    laboratoryId, shift.getId(), request.date(), request.slot());
            return Optional.empty();
        }

        ClassOccurrence occurrence = existing.orElseGet(() -> ClassOccurrence.builder()
                .institution(institutionRepository.getReferenceById(UUID.fromString(TenantContext.getInstitutionId())))
                .shift(shift)
                .laboratory(laboratory)
                .date(request.date())
                .slot((short) request.slot())
                .build());
        occurrence.setStationsUsed((short) request.stationsUsed());
        occurrence = occurrenceRepository.save(occurrence);
        log.info("Occurrence saved: lab={}, shift={}, date={}, slot={}, stations={}",
                laboratoryId, shift.getId(), request.date(), request.slot(), request.stationsUsed());

        return Optional.of(toDTO(occurrence, gridStations));
    }

    @Transactional
    public void delete(UUID periodId, UUID laboratoryId, UUID shiftId, LocalDate date, int slot) {
        AcademicPeriod period = getPeriod(periodId);
        findShift(period, shiftId);
        occurrenceRepository.findByShiftIdAndLaboratoryIdAndDateAndSlot(shiftId, laboratoryId, date, (short) slot)
                .ifPresent(occurrence -> {
                    occurrenceRepository.delete(occurrence);
                    log.info("Occurrence deleted: lab={}, shift={}, date={}, slot={}", laboratoryId, shiftId, date, slot);
                });
    }

    @Transactional(readOnly = true)
    public DayClassesDTO dayClasses(LocalDate requestedDate) {
        LocalDate date = requestedDate != null ? requestedDate : LocalDate.now(clock);

        Optional<AcademicPeriod> periodOpt = periodRepository.findAll().stream()
                .filter(p -> !date.isBefore(p.getStartDate()) && !date.isAfter(p.getEndDate()))
                .findFirst();
        if (periodOpt.isEmpty()) {
            return new DayClassesDTO(date, null, null, false, null, List.of());
        }
        AcademicPeriod period = periodOpt.get();

        String holidayName = period.getHolidays().stream()
                .filter(h -> h.getDate().equals(date))
                .map(AcademicPeriodHoliday::getDescription)
                .findFirst().orElse(null);
        boolean schoolDay = holidayName == null && period.getShifts().stream()
                .anyMatch(s -> ClassSessionExpander.isSchoolDayForShift(period, s, date));

        Map<UUID, Laboratory> labsById = laboratoryRepository.findAll().stream()
                .filter(Laboratory::isActive)
                .collect(Collectors.toMap(Laboratory::getId, Function.identity()));
        Map<UUID, Integer> capacity = new HashMap<>();

        List<DayClassesDTO.DayClass> classes = ClassSessionExpander.expand(period,
                        scheduleRepository.findByPeriodId(period.getId()),
                        occurrenceRepository.findByPeriodIdAndDateBetween(period.getId(), date, date),
                        date, date).stream()
                .filter(s -> labsById.containsKey(s.laboratoryId()))
                .sorted(Comparator.comparing((ClassSessionExpander.Session s) -> s.startTime())
                        .thenComparing(s -> labsById.get(s.laboratoryId()).getName()))
                .map(s -> new DayClassesDTO.DayClass(
                        s.laboratoryId(),
                        labsById.get(s.laboratoryId()).getName(),
                        capacity.computeIfAbsent(s.laboratoryId(), labEquipmentRepository::sumQuantityByLaboratoryId),
                        s.shift().getId(),
                        s.shift().getShiftType(),
                        s.slot(),
                        s.startTime().format(TIME_FMT),
                        s.endTime().format(TIME_FMT),
                        s.gridStations(),
                        s.stationsUsed(),
                        s.status()))
                .toList();

        return new DayClassesDTO(date, period.getId(), period.getName(), schoolDay, holidayName, classes);
    }

    private void validate(AcademicPeriod period, AcademicPeriodShift shift, UpsertClassOccurrenceRequest request,
                          int capacity) {
        LocalDate date = request.date();
        if (date == null) {
            throw new OccurrenceValidationException("Informe a data da aula");
        }
        if (date.isBefore(period.getStartDate()) || date.isAfter(period.getEndDate())) {
            throw new OccurrenceValidationException("A data " + date + " está fora do período letivo " + period.getName());
        }
        if (period.getHolidays().stream().anyMatch(h -> h.getDate().equals(date))) {
            throw new OccurrenceValidationException("A data " + date + " é feriado ou recesso");
        }
        if (!ClassSessionExpander.isSchoolDayForShift(period, shift, date)) {
            throw new OccurrenceValidationException("O turno " + shift.getShiftType() + " não tem aulas em " + date);
        }
        if (request.slot() < 1 || request.slot() > shift.getClassesPerDay()) {
            throw new OccurrenceValidationException(
                    "Aula " + request.slot() + " fora do range [1, " + shift.getClassesPerDay() + "] para o turno " + shift.getShiftType());
        }
        if (request.stationsUsed() < 0) {
            throw new OccurrenceValidationException("O número de estações usadas não pode ser negativo");
        }
        if (capacity > 0 && request.stationsUsed() > capacity) {
            throw new OccurrenceValidationException(
                    "O laboratório tem " + capacity + " estações; não é possível usar " + request.stationsUsed());
        }
    }

    private AcademicPeriodShift findShift(AcademicPeriod period, UUID shiftId) {
        return period.getShifts().stream()
                .filter(s -> s.getId().equals(shiftId))
                .findFirst()
                .orElseThrow(() -> new OccurrenceValidationException("Turno não encontrado no período: " + shiftId));
    }

    private AcademicPeriod getPeriod(UUID periodId) {
        return periodRepository.findById(periodId).orElseThrow(() -> new PeriodNotFoundException(periodId));
    }

    private static Map<String, Integer> gridStations(List<LaboratorySchedule> schedules) {
        Map<String, Integer> result = new HashMap<>();
        for (LaboratorySchedule schedule : schedules) {
            short[] slots = schedule.getOccupiedSlots();
            short[] stations = schedule.getStationsUsed();
            for (int i = 0; i < slots.length; i++) {
                result.put(gridKey(schedule.getLaboratory().getId(), schedule.getShift().getId(),
                        schedule.getDayOfWeek(), slots[i]), (int) stations[i]);
            }
        }
        return result;
    }

    private static String gridKey(UUID labId, UUID shiftId, int dayOfWeek, int slot) {
        return labId + "|" + shiftId + "|" + dayOfWeek + "|" + slot;
    }

    private static ClassOccurrenceDTO toDTO(ClassOccurrence o, Integer gridStations) {
        return new ClassOccurrenceDTO(
                o.getId(),
                o.getLaboratory().getId(),
                o.getLaboratory().getName(),
                o.getShift().getId(),
                o.getShift().getShiftType(),
                o.getDate(),
                o.getSlot(),
                gridStations,
                o.getStationsUsed(),
                ClassSessionStatus.of(gridStations, o.getStationsUsed()));
    }
}
