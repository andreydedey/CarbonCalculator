package com.example.carboncalculator.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateMonitorRequest;
import com.example.carboncalculator.dto.MonitorDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.exceptions.MissingMonitorNameException;
import com.example.carboncalculator.exceptions.MonitorHasDependentsException;
import com.example.carboncalculator.exceptions.MonitorNotFoundException;
import com.example.carboncalculator.mappers.MonitorMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.ConfigurationRepository;
import com.example.carboncalculator.repositories.MonitorRepository;
import com.example.carboncalculator.specifications.MonitorSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final MonitorRepository monitorRepository;
    private final ConfigurationRepository configurationRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public MonitorDTO create(CreateMonitorRequest request) {
        validateName(request.name());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        Monitor monitor = Monitor.builder()
                .institution(institution)
                .name(request.name())
                .watts(request.watts())
                .build();

        MonitorDTO dto = MonitorMapper.toDTO(monitorRepository.save(monitor));
        log.info("Monitor created: id={}", dto.id());
        return dto;
    }

    public MonitorDTO getById(UUID id) {
        return MonitorMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public MonitorDTO update(UUID id, CreateMonitorRequest request) {
        validateName(request.name());

        Monitor monitor = getOrThrow(id);
        monitor.setName(request.name());
        monitor.setWatts(request.watts());

        log.info("Monitor updated: id={}", id);
        return MonitorMapper.toDTO(monitorRepository.save(monitor));
    }

    public Page<MonitorDTO> list(String name, Pageable pageable) {
        Specification<Monitor> spec = Specification.unrestricted();
        if (name != null && !name.isBlank()) {
            spec = spec.and(MonitorSpecification.nameContains(name));
        }
        return monitorRepository.findAll(spec, pageable).map(MonitorMapper::toDTO);
    }

    @Transactional
    public void delete(UUID id) {
        Monitor monitor = getOrThrow(id);
        if (configurationRepository.existsByMonitorId(id)) {
            throw new MonitorHasDependentsException(id);
        }
        monitorRepository.delete(monitor);
        log.info("Monitor deleted: id={}", id);
    }

    Monitor getOrThrow(UUID id) {
        return monitorRepository.findById(id)
                .orElseThrow(() -> new MonitorNotFoundException(id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MissingMonitorNameException();
        }
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
