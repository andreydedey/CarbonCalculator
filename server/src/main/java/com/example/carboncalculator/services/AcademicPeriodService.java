package com.example.carboncalculator.services;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.CopyPeriodRequest;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.HolidayDTO;
import com.example.carboncalculator.dto.ReplaceHolidaysRequest;
import com.example.carboncalculator.dto.UpdateAcademicPeriodRequest;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.AcademicPeriodHoliday;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.LaboratorySchedule;
import com.example.carboncalculator.exceptions.HolidayOutOfRangeException;
import com.example.carboncalculator.exceptions.PeriodNotFoundException;
import com.example.carboncalculator.exceptions.PeriodOverlapException;
import com.example.carboncalculator.repositories.AcademicPeriodHolidayRepository;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicPeriodService {

    private static final Logger log = LoggerFactory.getLogger(AcademicPeriodService.class);

    private final AcademicPeriodRepository periodRepository;
    private final AcademicPeriodHolidayRepository holidayRepository;
    private final LaboratoryScheduleRepository scheduleRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional(readOnly = true)
    public Page<AcademicPeriodDTO> list(Pageable pageable) {
        return periodRepository.findAllOrdered(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public AcademicPeriodDTO getById(UUID id) {
        return toDTO(getOrThrow(id));
    }

    @Transactional
    public AcademicPeriodDTO create(CreateAcademicPeriodRequest request) {
        validateDates(request.startDate(), request.endDate());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        AcademicPeriod period = AcademicPeriod.builder()
                .institution(institution)
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        AcademicPeriodDTO dto = toDTO(saveCatchingOverlap(period));
        log.info("Academic period created: id={}", dto.id());
        return dto;
    }

    @Transactional
    public AcademicPeriodDTO update(UUID id, UpdateAcademicPeriodRequest request) {
        validateDates(request.startDate(), request.endDate());

        AcademicPeriod period = getOrThrow(id);
        period.setName(request.name());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());

        AcademicPeriodDTO dto = toDTO(saveCatchingOverlap(period));
        log.info("Academic period updated: id={}", id);
        return dto;
    }

    @Transactional
    public void delete(UUID id) {
        AcademicPeriod period = getOrThrow(id);
        periodRepository.delete(period);
        log.info("Academic period deleted: id={}", id);
    }

    @Transactional
    public List<HolidayDTO> replaceHolidays(UUID periodId, ReplaceHolidaysRequest request) {
        AcademicPeriod period = getOrThrow(periodId);

        for (HolidayDTO h : request.holidays()) {
            if (h.date().isBefore(period.getStartDate()) || h.date().isAfter(period.getEndDate())) {
                throw new HolidayOutOfRangeException(h.date(), period.getStartDate(), period.getEndDate());
            }
        }

        period.getHolidays().clear();
        periodRepository.flush();

        for (HolidayDTO h : request.holidays()) {
            AcademicPeriodHoliday holiday = AcademicPeriodHoliday.builder()
                    .academicPeriod(period)
                    .date(h.date())
                    .description(h.description())
                    .build();
            period.getHolidays().add(holiday);
        }
        periodRepository.save(period);

        log.info("Holidays replaced for period {}: {} holidays", periodId, request.holidays().size());
        return period.getHolidays().stream()
                .map(h -> new HolidayDTO(h.getDate(), h.getDescription()))
                .toList();
    }

    @Transactional
    public AcademicPeriodDTO copyPeriod(UUID sourcePeriodId, CopyPeriodRequest request) {
        validateDates(request.startDate(), request.endDate());

        AcademicPeriod source = getOrThrow(sourcePeriodId);
        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());

        AcademicPeriod newPeriod = AcademicPeriod.builder()
                .institution(institution)
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        newPeriod = saveCatchingOverlap(newPeriod);

        // Copy holidays that fall within the new period's range
        for (AcademicPeriodHoliday h : source.getHolidays()) {
            if (!h.getDate().isBefore(request.startDate()) && !h.getDate().isAfter(request.endDate())) {
                AcademicPeriodHoliday copy = AcademicPeriodHoliday.builder()
                        .academicPeriod(newPeriod)
                        .date(h.getDate())
                        .description(h.getDescription())
                        .build();
                newPeriod.getHolidays().add(copy);
            }
        }

        // Copy all schedule blocks
        List<LaboratorySchedule> sourceSchedules = scheduleRepository.findByAcademicPeriodId(source.getId());
        for (LaboratorySchedule s : sourceSchedules) {
            LaboratorySchedule copy = LaboratorySchedule.builder()
                    .academicPeriod(newPeriod)
                    .laboratory(s.getLaboratory())
                    .dayOfWeek(s.getDayOfWeek())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .build();
            newPeriod.getSchedules().add(copy);
        }

        newPeriod = periodRepository.save(newPeriod);
        log.info("Academic period copied: source={}, new={}", sourcePeriodId, newPeriod.getId());
        return toDTO(newPeriod);
    }

    private AcademicPeriod getOrThrow(UUID id) {
        return periodRepository.findById(id).orElseThrow(() -> new PeriodNotFoundException(id));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Datas de início e fim são obrigatórias");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Data final deve ser posterior à data inicial");
        }
    }

    private AcademicPeriod saveCatchingOverlap(AcademicPeriod period) {
        try {
            return periodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException e) {
            if (e.getMessage() != null && e.getMessage().contains("excl_academic_period_overlap")) {
                throw new PeriodOverlapException();
            }
            throw e;
        }
    }

    private AcademicPeriodDTO toDTO(AcademicPeriod period) {
        return new AcademicPeriodDTO(
                period.getId(),
                period.getName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getHolidays().size(),
                period.getCreatedAt());
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
