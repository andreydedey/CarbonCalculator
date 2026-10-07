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
import com.example.carboncalculator.dto.CreateOperatingSystemRequest;
import com.example.carboncalculator.dto.OperatingSystemDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.OperatingSystem;
import com.example.carboncalculator.exceptions.DuplicateOperatingSystemException;
import com.example.carboncalculator.exceptions.MissingOperatingSystemNameException;
import com.example.carboncalculator.exceptions.OperatingSystemHasDependentsException;
import com.example.carboncalculator.exceptions.OperatingSystemNotFoundException;
import com.example.carboncalculator.mappers.OperatingSystemMapper;
import com.example.carboncalculator.repositories.ConfigurationRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.OperatingSystemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OperatingSystemService {

    private static final Logger log = LoggerFactory.getLogger(OperatingSystemService.class);

    private final OperatingSystemRepository operatingSystemRepository;
    private final ConfigurationRepository configurationRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public OperatingSystemDTO create(CreateOperatingSystemRequest request) {
        validateName(request.name());
        checkDuplicate(request.name().trim());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        OperatingSystem os = OperatingSystem.builder()
                .institution(institution)
                .name(request.name().trim())
                .build();

        OperatingSystemDTO dto = OperatingSystemMapper.toDTO(operatingSystemRepository.save(os));
        log.info("OperatingSystem created: id={}", dto.id());
        return dto;
    }

    @Transactional(readOnly = true)
    public OperatingSystemDTO getById(UUID id) {
        return OperatingSystemMapper.toDTO(getOrThrow(id));
    }

    @Transactional
    public OperatingSystemDTO update(UUID id, CreateOperatingSystemRequest request) {
        validateName(request.name());

        OperatingSystem os = getOrThrow(id);
        if (!os.getName().equals(request.name().trim())) {
            checkDuplicate(request.name().trim());
        }
        os.setName(request.name().trim());

        log.info("OperatingSystem updated: id={}", id);
        return OperatingSystemMapper.toDTO(operatingSystemRepository.save(os));
    }

    @Transactional(readOnly = true)
    public Page<OperatingSystemDTO> list(String name, Pageable pageable) {
        Specification<OperatingSystem> spec = Specification.unrestricted();
        if (name != null && !name.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }
        return operatingSystemRepository.findAll(spec, pageable).map(OperatingSystemMapper::toDTO);
    }

    @Transactional
    public void delete(UUID id) {
        OperatingSystem os = getOrThrow(id);
        if (configurationRepository.existsByOperatingSystemId(id)) {
            throw new OperatingSystemHasDependentsException(id);
        }
        operatingSystemRepository.delete(os);
        log.info("OperatingSystem deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    OperatingSystem getOrThrow(UUID id) {
        return operatingSystemRepository.findById(id)
                .orElseThrow(() -> new OperatingSystemNotFoundException(id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MissingOperatingSystemNameException();
        }
    }

    private void checkDuplicate(String name) {
        Specification<OperatingSystem> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("institution").get("id"), currentInstitutionId()),
                        cb.equal(cb.lower(root.get("name")), name.toLowerCase()));
        if (operatingSystemRepository.count(spec) > 0) {
            throw new DuplicateOperatingSystemException();
        }
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }
}
