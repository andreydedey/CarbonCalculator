package com.example.carboncalculator.services;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.exceptions.LaboratoryHasDependentsException;
import com.example.carboncalculator.exceptions.LaboratoryNotFoundException;
import com.example.carboncalculator.exceptions.MissingLaboratoryNameException;
import com.example.carboncalculator.mappers.LaboratoryMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.specifications.LaboratorySpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaboratoryService {

    private final LaboratoryRepository laboratoryRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public LaboratoryDTO create(CreateLaboratoryRequest request) {
        validateName(request.name());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        Laboratory laboratory = Laboratory.builder()
                .institution(institution)
                .name(request.name())
                .description(request.description())
                .build();

        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    public LaboratoryDTO getById(UUID id) {
        return LaboratoryMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public LaboratoryDTO update(UUID id, CreateLaboratoryRequest request) {
        validateName(request.name());
        Laboratory laboratory = getOrThrow(id);
        laboratory.setName(request.name());
        laboratory.setDescription(request.description());
        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    public Page<LaboratoryDTO> list(Boolean active, String name, Pageable pageable) {
        Specification<Laboratory> spec = Specification.unrestricted();
        if (active != null) {
            spec = spec.and(LaboratorySpecification.hasActive(active));
        }
        if (name != null && !name.isBlank()) {
            spec = spec.and(LaboratorySpecification.nameContains(name));
        }
        return laboratoryRepository.findAll(spec, pageable).map(LaboratoryMapper::toDTO);
    }

    @Transactional
    public LaboratoryDTO activate(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        laboratory.setActive(true);
        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    @Transactional
    public LaboratoryDTO deactivate(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        laboratory.setActive(false);
        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    @Transactional
    public void delete(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        if (laboratoryRepository.existsDependentsByLaboratoryId(id)) {
            throw new LaboratoryHasDependentsException(id);
        }
        laboratoryRepository.delete(laboratory);
    }

    private Laboratory getOrThrow(UUID id) {
        return laboratoryRepository.findById(id).orElseThrow(() -> new LaboratoryNotFoundException(id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MissingLaboratoryNameException();
        }
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
