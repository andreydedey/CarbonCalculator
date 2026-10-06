package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.ConsumptionMeasurementDTO;
import com.example.carboncalculator.dto.ConsumptionMeasurementResponse;
import com.example.carboncalculator.dto.CreateConsumptionMeasurementRequest;
import com.example.carboncalculator.dto.OutlierWarningDTO;
import com.example.carboncalculator.entities.ConsumptionMeasurement;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.entities.OperatingSystem;
import com.example.carboncalculator.entities.TargetType;
import com.example.carboncalculator.exceptions.MeasurementNotFoundException;
import com.example.carboncalculator.mappers.ConsumptionMeasurementMapper;
import com.example.carboncalculator.repositories.ConsumptionMeasurementRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsumptionMeasurementService {

    private static final Logger log = LoggerFactory.getLogger(ConsumptionMeasurementService.class);
    private static final double OUTLIER_THRESHOLD = 0.50;
    private static final long MIN_MEASUREMENTS_FOR_OUTLIER = 2;

    private final ConsumptionMeasurementRepository measurementRepository;
    private final InstitutionRepository institutionRepository;
    private final EquipmentModelService equipmentModelService;
    private final MonitorService monitorService;
    private final OperatingSystemService operatingSystemService;

    @Transactional
    public ConsumptionMeasurementResponse create(CreateConsumptionMeasurementRequest request) {

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        ConsumptionMeasurement measurement = buildMeasurement(request, institution);
        measurement = measurementRepository.save(measurement);

        OutlierWarningDTO warning = checkOutlier(measurement);

        log.info("ConsumptionMeasurement created: id={}, targetType={}", measurement.getId(), measurement.getTargetType());
        return new ConsumptionMeasurementResponse(
                ConsumptionMeasurementMapper.toDTO(measurement), warning);
    }

    @Transactional(readOnly = true)
    public Page<ConsumptionMeasurementDTO> list(TargetType targetType, UUID equipmentModelId,
            UUID operatingSystemId, UUID monitorId, Pageable pageable) {
        Specification<ConsumptionMeasurement> spec = Specification.unrestricted();

        if (targetType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
        }
        if (equipmentModelId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("equipmentModel").get("id"), equipmentModelId));
        }
        if (operatingSystemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("operatingSystem").get("id"), operatingSystemId));
        }
        if (monitorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("monitor").get("id"), monitorId));
        }

        return measurementRepository.findAll(spec, pageable).map(ConsumptionMeasurementMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public ConsumptionMeasurementDTO getById(UUID id) {
        return ConsumptionMeasurementMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public ConsumptionMeasurementResponse update(UUID id, CreateConsumptionMeasurementRequest request) {
        ConsumptionMeasurement measurement = getOrThrow(id);

        EquipmentModel model = resolveModel(request);
        OperatingSystem os = resolveOs(request);
        Monitor monitor = resolveMonitor(request);

        measurement.setTargetType(request.targetType());
        measurement.setEquipmentModel(model);
        measurement.setOperatingSystem(os);
        measurement.setMonitor(monitor);
        measurement.setAverageWatts(request.averageWatts());
        measurement.setDurationMinutes(request.durationMinutes());
        measurement.setReadingIntervalMinutes(request.readingIntervalMinutes());
        measurement.setMeasurementDate(request.measurementDate());
        measurement.setConditions(request.conditions());
        measurement.setNotes(request.notes());

        measurement = measurementRepository.save(measurement);
        OutlierWarningDTO warning = checkOutlier(measurement);

        log.info("ConsumptionMeasurement updated: id={}", id);
        return new ConsumptionMeasurementResponse(
                ConsumptionMeasurementMapper.toDTO(measurement), warning);
    }

    @Transactional
    public void delete(UUID id) {
        ConsumptionMeasurement measurement = getOrThrow(id);
        measurementRepository.delete(measurement);
        log.info("ConsumptionMeasurement deleted: id={}", id);
    }

    private ConsumptionMeasurement getOrThrow(UUID id) {
        return measurementRepository.findById(id)
                .orElseThrow(() -> new MeasurementNotFoundException(id));
    }

    private ConsumptionMeasurement buildMeasurement(CreateConsumptionMeasurementRequest request, Institution institution) {
        return ConsumptionMeasurement.builder()
                .institution(institution)
                .targetType(request.targetType())
                .equipmentModel(resolveModel(request))
                .operatingSystem(resolveOs(request))
                .monitor(resolveMonitor(request))
                .averageWatts(request.averageWatts())
                .durationMinutes(request.durationMinutes())
                .readingIntervalMinutes(request.readingIntervalMinutes())
                .measurementDate(request.measurementDate())
                .conditions(request.conditions())
                .notes(request.notes())
                .build();
    }

    private EquipmentModel resolveModel(CreateConsumptionMeasurementRequest request) {
        return request.equipmentModelId() != null
                ? equipmentModelService.getOrThrow(request.equipmentModelId())
                : null;
    }

    private OperatingSystem resolveOs(CreateConsumptionMeasurementRequest request) {
        return request.operatingSystemId() != null
                ? operatingSystemService.getOrThrow(request.operatingSystemId())
                : null;
    }

    private Monitor resolveMonitor(CreateConsumptionMeasurementRequest request) {
        return request.monitorId() != null
                ? monitorService.getOrThrow(request.monitorId())
                : null;
    }

    private OutlierWarningDTO checkOutlier(ConsumptionMeasurement measurement) {
        UUID modelId = measurement.getEquipmentModel() != null ? measurement.getEquipmentModel().getId() : null;
        UUID osId = measurement.getOperatingSystem() != null ? measurement.getOperatingSystem().getId() : null;
        UUID monitorId = measurement.getMonitor() != null ? measurement.getMonitor().getId() : null;

        long count = measurementRepository.countForTarget(
                measurement.getTargetType(), modelId, osId, monitorId, measurement.getId());

        if (count < MIN_MEASUREMENTS_FOR_OUTLIER) {
            return null;
        }

        Double avg = measurementRepository.findAverageWattsForTarget(
                measurement.getTargetType(), modelId, osId, monitorId, measurement.getId());

        if (avg == null || avg == 0) {
            return null;
        }

        double deviation = Math.abs(measurement.getAverageWatts().doubleValue() - avg) / avg;

        if (deviation > OUTLIER_THRESHOLD) {
            return new OutlierWarningDTO(
                    BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP),
                    measurement.getAverageWatts(),
                    Math.round(deviation * 10000.0) / 100.0);
        }

        return null;
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
