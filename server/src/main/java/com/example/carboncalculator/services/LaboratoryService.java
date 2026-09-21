package com.example.carboncalculator.services;

import java.util.List;
import java.util.UUID;

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
                .build();

        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    public List<LaboratoryDTO> list(boolean includeInactive) {
        List<Laboratory> laboratories = includeInactive
                ? laboratoryRepository.findAll()
                : laboratoryRepository.findByActiveTrue();

        return laboratories.stream().map(LaboratoryMapper::toDTO).toList();
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
