package com.example.carboncalculator.services;

import java.util.UUID;

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
import com.example.carboncalculator.exceptions.MissingEquipmentModelNameException;
import com.example.carboncalculator.mappers.EquipmentModelMapper;
import com.example.carboncalculator.repositories.EquipmentModelRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.specifications.EquipmentModelSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EquipmentModelService {

    private final EquipmentModelRepository equipmentModelRepository;
    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public EquipmentModelDTO create(CreateEquipmentModelRequest request) {
        validateName(request.name());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        EquipmentModel model = EquipmentModel.builder()
                .institution(institution)
                .name(request.name())
                .processor(request.processor())
                .memoryGb(request.memoryGb())
                .hasDedicatedGpu(request.hasDedicatedGpu() != null && request.hasDedicatedGpu())
                .gpuModel(request.gpuModel())
                .monitorName(request.monitorName())
                .monitorSizeInches(request.monitorSizeInches())
                .monitorResolution(request.monitorResolution())
                .build();

        return EquipmentModelMapper.toDTO(equipmentModelRepository.save(model));
    }

    public EquipmentModelDTO getById(UUID id) {
        return EquipmentModelMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public EquipmentModelDTO update(UUID id, CreateEquipmentModelRequest request) {
        validateName(request.name());

        EquipmentModel model = getOrThrow(id);
        model.setName(request.name());
        model.setProcessor(request.processor());
        model.setMemoryGb(request.memoryGb());
        model.setHasDedicatedGpu(request.hasDedicatedGpu() != null && request.hasDedicatedGpu());
        model.setGpuModel(request.gpuModel());
        model.setMonitorName(request.monitorName());
        model.setMonitorSizeInches(request.monitorSizeInches());
        model.setMonitorResolution(request.monitorResolution());

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
        if (laboratoryEquipmentRepository.existsByEquipmentModelId(id)) {
            throw new EquipmentModelHasDependentsException(id);
        }
        equipmentModelRepository.delete(model);
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

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
