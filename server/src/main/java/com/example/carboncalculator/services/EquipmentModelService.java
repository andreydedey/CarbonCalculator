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
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.exceptions.EquipmentModelHasDependentsException;
import com.example.carboncalculator.exceptions.EquipmentModelNotFoundException;
import com.example.carboncalculator.exceptions.GpuTdpRequiredException;
import com.example.carboncalculator.exceptions.MissingEquipmentModelNameException;
import com.example.carboncalculator.mappers.EquipmentModelMapper;
import com.example.carboncalculator.repositories.ConfigurationRepository;
import com.example.carboncalculator.repositories.EquipmentModelRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.specifications.EquipmentModelSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EquipmentModelService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentModelService.class);

    private final EquipmentModelRepository equipmentModelRepository;
    private final ConfigurationRepository configurationRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public EquipmentModelDTO create(CreateEquipmentModelRequest request) {
        validateName(request.name());
        validateGpu(request);

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        EquipmentModel model = EquipmentModel.builder()
                .institution(institution)
                .name(request.name())
                .equipmentType(request.equipmentType())
                .processor(request.processor())
                .tdpWatts(request.tdpWatts())
                .coreCount(request.coreCount())
                .memoryGb(request.memoryGb())
                .gpuModel(request.gpuModel())
                .gpuTdpWatts(request.gpuTdpWatts())
                .hasIntegratedScreen(Boolean.TRUE.equals(request.hasIntegratedScreen()))
                .description(request.description())
                .build();

        EquipmentModelDTO dto = EquipmentModelMapper.toDTO(equipmentModelRepository.save(model));
        log.info("Equipment model created: id={}", dto.id());
        return dto;
    }

    public EquipmentModelDTO getById(UUID id) {
        return EquipmentModelMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public EquipmentModelDTO update(UUID id, CreateEquipmentModelRequest request) {
        validateName(request.name());
        validateGpu(request);

        EquipmentModel model = getOrThrow(id);
        model.setName(request.name());
        model.setEquipmentType(request.equipmentType());
        model.setProcessor(request.processor());
        model.setTdpWatts(request.tdpWatts());
        model.setCoreCount(request.coreCount());
        model.setMemoryGb(request.memoryGb());
        model.setGpuModel(request.gpuModel());
        model.setGpuTdpWatts(request.gpuTdpWatts());
        model.setHasIntegratedScreen(Boolean.TRUE.equals(request.hasIntegratedScreen()));
        model.setDescription(request.description());

        log.info("Equipment model updated: id={}", id);
        return EquipmentModelMapper.toDTO(equipmentModelRepository.save(model));
    }

    public Page<EquipmentModelDTO> list(String name, Pageable pageable) {
        Specification<EquipmentModel> spec = Specification.unrestricted();
        if (name != null && !name.isBlank()) {
            spec = spec.and(EquipmentModelSpecification.nameContains(name));
        }
        return equipmentModelRepository.findAll(spec, pageable).map(EquipmentModelMapper::toDTO);
    }

    @Transactional
    public void delete(UUID id) {
        EquipmentModel model = getOrThrow(id);
        if (configurationRepository.existsByEquipmentModelId(id)) {
            throw new EquipmentModelHasDependentsException(id);
        }
        equipmentModelRepository.delete(model);
        log.info("Equipment model deleted: id={}", id);
    }

    EquipmentModel getOrThrow(UUID id) {
        return equipmentModelRepository.findById(id)
                .orElseThrow(() -> new EquipmentModelNotFoundException(id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MissingEquipmentModelNameException();
        }
    }

    private void validateGpu(CreateEquipmentModelRequest request) {
        if (request.gpuModel() != null && !request.gpuModel().isBlank() && request.gpuTdpWatts() == null) {
            throw new GpuTdpRequiredException();
        }
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
