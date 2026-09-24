package com.example.carboncalculator.services;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.specifications.LaboratorySpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaboratoryService {

    private static final Logger log = LoggerFactory.getLogger(LaboratoryService.class);

    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository;
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

        LaboratoryDTO dto = LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
        log.info("Laboratory created: id={}", dto.id());
        return dto;
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
        log.info("Laboratory updated: id={}", id);
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
        Page<Laboratory> page = laboratoryRepository.findAll(spec, pageable);

        var labIds = page.getContent().stream().map(Laboratory::getId).toList();
        Map<UUID, int[]> statsMap = new HashMap<>();
        if (!labIds.isEmpty()) {
            for (Object[] row : laboratoryEquipmentRepository.countStationsByLaboratoryIds(labIds)) {
                UUID labId = (UUID) row[0];
                int configCount = ((Number) row[1]).intValue();
                int totalStations = ((Number) row[2]).intValue();
                statsMap.put(labId, new int[] { configCount, totalStations });
            }
        }

        return page.map(lab -> {
            int[] stats = statsMap.getOrDefault(lab.getId(), new int[] { 0, 0 });
            return LaboratoryMapper.toDTO(lab, stats[0], stats[1]);
        });
    }

    @Transactional
    public LaboratoryDTO activate(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        laboratory.setActive(true);
        log.info("Laboratory activated: id={}", id);
        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    @Transactional
    public LaboratoryDTO deactivate(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        laboratory.setActive(false);
        log.info("Laboratory deactivated: id={}", id);
        return LaboratoryMapper.toDTO(laboratoryRepository.save(laboratory));
    }

    @Transactional
    public void delete(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        if (laboratoryRepository.existsDependentsByLaboratoryId(id)) {
            throw new LaboratoryHasDependentsException(id);
        }
        laboratoryRepository.delete(laboratory);
        log.info("Laboratory deleted: id={}", id);
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
