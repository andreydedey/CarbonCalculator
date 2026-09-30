package com.example.carboncalculator.services;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.entities.EmissionFactor;
import com.example.carboncalculator.exceptions.DuplicateEmissionFactorException;
import com.example.carboncalculator.exceptions.EmissionFactorNotFoundException;
import com.example.carboncalculator.exceptions.InvalidEmissionFactorException;
import com.example.carboncalculator.repositories.EmissionFactorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmissionFactorService {

    private static final Logger log = LoggerFactory.getLogger(EmissionFactorService.class);

    private final EmissionFactorRepository repository;

    @Transactional
    public EmissionFactorDTO create(CreateEmissionFactorRequest request) {
        validate(request);

        if (repository.existsByYearAndMonth(request.year(), request.month())) {
            throw new DuplicateEmissionFactorException(request.year(), request.month());
        }

        EmissionFactor factor = EmissionFactor.builder()
                .year(request.year())
                .month(request.month())
                .value(request.value())
                .source(request.source().trim())
                .build();

        EmissionFactorDTO dto = toDTO(repository.save(factor));
        log.info("Emission factor created: id={}, {}/{}", dto.id(), request.month(), request.year());
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<EmissionFactorDTO> list(Short year, Pageable pageable) {
        Page<EmissionFactor> page = year != null
                ? repository.findByYear(year, pageable)
                : repository.findAll(pageable);
        return page.map(this::toDTO);
    }

    @Transactional
    public EmissionFactorDTO update(UUID id, CreateEmissionFactorRequest request) {
        validate(request);

        EmissionFactor factor = getOrThrow(id);

        if (repository.existsByYearAndMonthAndIdNot(request.year(), request.month(), id)) {
            throw new DuplicateEmissionFactorException(request.year(), request.month());
        }

        factor.setYear(request.year());
        factor.setMonth(request.month());
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
        if (request.year() == null) {
            throw new InvalidEmissionFactorException("O ano é obrigatório.");
        }
        if (request.month() == null || request.month() < 1 || request.month() > 12) {
            throw new InvalidEmissionFactorException("O mês deve estar entre 1 e 12.");
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
                factor.getYear(),
                factor.getMonth(),
                factor.getValue(),
                factor.getSource(),
                factor.getCreatedAt());
    }
}
