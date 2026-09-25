package com.example.carboncalculator.services;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ScheduleBlockDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.exceptions.LaboratoryNotFoundException;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.exceptions.ScheduleBlockOverlapException;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaboratoryScheduleService {

    private static final Logger log = LoggerFactory.getLogger(LaboratoryScheduleService.class);

    private final LaboratoryScheduleRepository scheduleRepository;
    private final AcademicPeriodRepository periodRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Transactional(readOnly = true)
    public List<ScheduleBlockDTO> getSchedule(UUID periodId, UUID laboratoryId) {
        return scheduleRepository.findByAcademicPeriodIdAndLaboratoryId(periodId, laboratoryId)
                .stream()
                .map(s -> new ScheduleBlockDTO(s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList();
    }

    @Transactional
    public List<ScheduleBlockDTO> replaceSchedule(UUID periodId, UUID laboratoryId, ReplaceScheduleRequest request) {
        AcademicPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new PeriodNotFoundException(periodId));
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId)
                .orElseThrow(() -> new LaboratoryNotFoundException(laboratoryId));

        validateBlocks(request.blocks());

        scheduleRepository.deleteByAcademicPeriodIdAndLaboratoryId(periodId, laboratoryId);
        scheduleRepository.flush();

        List<LaboratorySchedule> newSchedules = request.blocks().stream()
                .map(block -> LaboratorySchedule.builder()
                        .academicPeriod(period)
                        .laboratory(laboratory)
                        .dayOfWeek((short) block.dayOfWeek())
                        .startTime(block.startTime())
                        .endTime(block.endTime())
                        .build())
                .toList();

        scheduleRepository.saveAll(newSchedules);
        log.info("Schedule replaced for period={}, lab={}: {} blocks", periodId, laboratoryId, request.blocks().size());

        return request.blocks();
    }

    private void validateBlocks(List<ScheduleBlockDTO> blocks) {
        for (ScheduleBlockDTO block : blocks) {
            if (block.dayOfWeek() < 1 || block.dayOfWeek() > 6) {
                throw new IllegalArgumentException("Dia da semana deve estar entre 1 (segunda) e 6 (sábado)");
            }
            if (!block.endTime().isAfter(block.startTime())) {
                throw new IllegalArgumentException("Horário final deve ser posterior ao horário inicial");
            }
        }

        // Check for overlaps within the same day
        Map<Integer, List<ScheduleBlockDTO>> byDay = blocks.stream()
                .collect(Collectors.groupingBy(ScheduleBlockDTO::dayOfWeek));

        for (var entry : byDay.entrySet()) {
            List<ScheduleBlockDTO> dayBlocks = entry.getValue().stream()
                    .sorted(Comparator.comparing(ScheduleBlockDTO::startTime))
                    .toList();

            for (int i = 0; i < dayBlocks.size() - 1; i++) {
                if (dayBlocks.get(i).endTime().isAfter(dayBlocks.get(i + 1).startTime())) {
                    throw new ScheduleBlockOverlapException(entry.getKey());
                }
            }
        }
    }
}
