package com.example.carboncalculator.services;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodShift;
import com.example.carboncalculator.entities.ShiftType;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.exceptions.ShiftValidationException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.AcademicPeriodShiftRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

    private final AcademicPeriodRepository periodRepository;
    private final AcademicPeriodShiftRepository shiftRepository;

    @Transactional
    public List<ShiftDTO> replaceShifts(UUID periodId, ReplaceShiftsRequest request) {
        AcademicPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new PeriodNotFoundException(periodId));

        validateShifts(request.shifts());

        period.getShifts().clear();
        periodRepository.flush();

        for (ReplaceShiftsRequest.ShiftInput input : request.shifts()) {
            LocalTime endTime = calculateEndTime(input);

            AcademicPeriodShift shift = AcademicPeriodShift.builder()
                    .academicPeriod(period)
                    .shiftType(input.shiftType())
                    .startTime(input.startTime())
                    .endTime(endTime)
                    .classesPerDay((short) input.classesPerDay())
                    .classDurationMinutes((short) input.classDurationMinutes())
                    .breakDurationMinutes((short) input.breakDurationMinutes())
                    .activeDays(toShortArray(input.activeDays()))
                    .enabled(input.enabled())
                    .build();
            period.getShifts().add(shift);
        }

        periodRepository.save(period);

        log.info("Shifts replaced for period {}: {} shifts", periodId, request.shifts().size());
        return period.getShifts().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftDTO> getShifts(UUID periodId) {
        if (!periodRepository.existsById(periodId)) {
            throw new PeriodNotFoundException(periodId);
        }
        return shiftRepository.findByAcademicPeriodId(periodId).stream()
                .map(this::toDTO)
                .toList();
    }

    private void validateShifts(List<ReplaceShiftsRequest.ShiftInput> shifts) {
        Set<ShiftType> seenTypes = new HashSet<>();

        for (ReplaceShiftsRequest.ShiftInput input : shifts) {
            if (input.shiftType() == null) {
                throw new ShiftValidationException("Tipo do turno é obrigatório");
            }

            if (!seenTypes.add(input.shiftType())) {
                throw new ShiftValidationException(
                        "Tipo de turno duplicado: " + input.shiftType());
            }

            if (input.classesPerDay() <= 0) {
                throw new ShiftValidationException(
                        "Aulas por dia deve ser maior que zero");
            }

            if (input.classDurationMinutes() <= 0) {
                throw new ShiftValidationException(
                        "Duração da aula deve ser maior que zero");
            }

            if (input.breakDurationMinutes() < 0) {
                throw new ShiftValidationException(
                        "Duração do intervalo não pode ser negativa");
            }

            if (input.startTime() == null) {
                throw new ShiftValidationException("Horário de início é obrigatório");
            }

            if (input.activeDays() == null || input.activeDays().isEmpty()) {
                throw new ShiftValidationException("Dias ativos são obrigatórios");
            }

            for (int day : input.activeDays()) {
                if (day < 1 || day > 6) {
                    throw new ShiftValidationException(
                            "Dia ativo inválido: " + day + ". Valores permitidos: 1 (segunda) a 6 (sábado)");
                }
            }
        }
    }

    private LocalTime calculateEndTime(ReplaceShiftsRequest.ShiftInput input) {
        int totalMinutes = (input.classesPerDay() * input.classDurationMinutes())
                + ((input.classesPerDay() - 1) * input.breakDurationMinutes());
        return input.startTime().plusMinutes(totalMinutes);
    }

    private short[] toShortArray(List<Integer> list) {
        short[] arr = new short[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).shortValue();
        }
        return arr;
    }

    ShiftDTO toDTO(AcademicPeriodShift shift) {
        return new ShiftDTO(
                shift.getId(),
                shift.getShiftType(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getClassesPerDay(),
                shift.getClassDurationMinutes(),
                shift.getBreakDurationMinutes(),
                toIntList(shift.getActiveDays()),
                shift.isEnabled());
    }

    private List<Integer> toIntList(short[] arr) {
        if (arr == null) return List.of();
        return java.util.Arrays.stream(toBoxedArray(arr)).toList();
    }

    private Integer[] toBoxedArray(short[] arr) {
        Integer[] result = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = (int) arr[i];
        }
        return result;
    }
}
