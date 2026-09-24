package com.example.carboncalculator.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.ConfigurationDTO;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Monitor;
import com.example.carboncalculator.exceptions.ConfigurationHasDependentsException;
import com.example.carboncalculator.exceptions.ConfigurationNotFoundException;
import com.example.carboncalculator.exceptions.DuplicateConfigurationException;
import com.example.carboncalculator.mappers.ConfigurationMapper;
import com.example.carboncalculator.repositories.ConfigurationRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationRepository configurationRepository;
    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository;
    private final InstitutionRepository institutionRepository;
    private final EquipmentModelService equipmentModelService;
    private final MonitorService monitorService;

    @Transactional
    public ConfigurationDTO create(CreateConfigurationRequest request) {
        UUID institutionId = currentInstitutionId();

        if (configurationRepository.existsDuplicate(
                institutionId, request.equipmentModelId(), request.operatingSystem(), request.monitorId())) {
            throw new DuplicateConfigurationException();
        }

        Institution institution = institutionRepository.getReferenceById(institutionId);
        EquipmentModel model = equipmentModelService.getOrThrow(request.equipmentModelId());
        Monitor monitor = request.monitorId() != null ? monitorService.getOrThrow(request.monitorId()) : null;

        Configuration config = Configuration.builder()
                .institution(institution)
                .equipmentModel(model)
                .operatingSystem(request.operatingSystem())
                .monitor(monitor)
                .build();

        config = configurationRepository.save(config);
        log.info("Configuration created: id={}", config.getId());
        return ConfigurationMapper.toDTO(config, 0, 0);
    }

    @Transactional(readOnly = true)
    public Page<ConfigurationDTO> list(Pageable pageable) {
        return configurationRepository.findAll(pageable).map(this::toDTOWithUsage);
    }

    @Transactional(readOnly = true)
    public ConfigurationDTO getById(UUID id) {
        Configuration config = getOrThrow(id);
        return toDTOWithUsage(config);
    }

    @Transactional
    public ConfigurationDTO update(UUID id, CreateConfigurationRequest request) {
        Configuration config = getOrThrow(id);
        UUID institutionId = currentInstitutionId();

        if (configurationRepository.existsDuplicateExcluding(
                institutionId, request.equipmentModelId(), request.operatingSystem(), request.monitorId(), id)) {
            throw new DuplicateConfigurationException();
        }

        EquipmentModel model = equipmentModelService.getOrThrow(request.equipmentModelId());
        Monitor monitor = request.monitorId() != null ? monitorService.getOrThrow(request.monitorId()) : null;

        config.setEquipmentModel(model);
        config.setOperatingSystem(request.operatingSystem());
        config.setMonitor(monitor);

        config = configurationRepository.save(config);
        log.info("Configuration updated: id={}", id);
        return toDTOWithUsage(config);
    }

    @Transactional
    public void delete(UUID id) {
        Configuration config = getOrThrow(id);
        if (laboratoryEquipmentRepository.existsByConfigurationId(id)) {
            throw new ConfigurationHasDependentsException(id);
        }
        configurationRepository.delete(config);
        log.info("Configuration deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    Configuration getOrThrow(UUID id) {
        return configurationRepository.findById(id)
                .orElseThrow(() -> new ConfigurationNotFoundException(id));
    }

    private ConfigurationDTO toDTOWithUsage(Configuration config) {
        int labCount = laboratoryEquipmentRepository.countDistinctLaboratoriesByConfigurationId(config.getId());
        int stationCount = laboratoryEquipmentRepository.sumQuantityByConfigurationId(config.getId());
        return ConfigurationMapper.toDTO(config, labCount, stationCount);
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
