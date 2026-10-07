package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.exceptions.DuplicateEmissionFactorException;
import com.example.carboncalculator.exceptions.EmissionFactorNotFoundException;
import com.example.carboncalculator.exceptions.InvalidEmissionFactorException;
import com.example.carboncalculator.repositories.EmissionFactorRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.specifications.EmissionFactorSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmissionFactorService {

    private static final Logger log = LoggerFactory.getLogger(EmissionFactorService.class);

    private final EmissionFactorRepository repository;
    private final InstitutionRepository institutionRepository;

    @Transactional
    public EmissionFactorDTO create(CreateEmissionFactorRequest request) {
        validate(request);

        if (repository.existsByReferenceMonth(request.referenceMonth())) {
            throw new DuplicateEmissionFactorException(request.referenceMonth());
        }

        Institution institution = institutionRepository.getReferenceById(
                UUID.fromString(TenantContext.getInstitutionId()));

        EmissionFactor factor = EmissionFactor.builder()
                .referenceMonth(request.referenceMonth())
                .value(request.value())
                .source(request.source().trim())
                .institution(institution)
                .build();

        EmissionFactorDTO dto = toDTO(repository.save(factor));
        log.info("Emission factor created: id={}, month={}", dto.id(), dto.referenceMonth());
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<EmissionFactorDTO> list(Integer year, Pageable pageable) {
        Specification<EmissionFactor> spec = EmissionFactorSpecification.referenceMonthInYear(year);
        return repository.findAllProjected(spec, pageable);
    }

    @Transactional
    public EmissionFactorDTO update(UUID id, CreateEmissionFactorRequest request) {
        validate(request);

        EmissionFactor factor = getOrThrow(id);

        if (repository.existsByReferenceMonthAndIdNot(request.referenceMonth(), id)) {
            throw new DuplicateEmissionFactorException(request.referenceMonth());
        }

        factor.setReferenceMonth(request.referenceMonth());
        factor.setValue(request.value());
        factor.setSource(request.source().trim());

        log.info("Emission factor updated: id={}", id);
        return toDTO(repository.save(factor));
    }

    @Transactional
    public void delete(UUID id) {
        EmissionFactor factor = getOrThrow(id);
        repository.delete(factor);
        log.info("Emission factor deleted: id={}", id);
    }

    EmissionFactor getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EmissionFactorNotFoundException(id));
    }

    private void validate(CreateEmissionFactorRequest request) {
        if (request.referenceMonth() == null) {
            throw new InvalidEmissionFactorException("O mês de referência é obrigatório.");
        }
        if (request.value() == null || request.value().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidEmissionFactorException("O valor do fator deve ser maior que zero.");
        }
        if (request.source() == null || request.source().isBlank()) {
            throw new InvalidEmissionFactorException("A fonte é obrigatória.");
        }
    }

    private EmissionFactorDTO toDTO(EmissionFactor factor) {
        return new EmissionFactorDTO(
                factor.getId(),
                factor.getReferenceMonth(),
                factor.getValue(),
                factor.getSource());
    }
}
