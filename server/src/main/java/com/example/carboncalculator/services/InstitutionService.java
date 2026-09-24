package com.example.carboncalculator.services;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.UpdateInstitutionRequest;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.exceptions.DuplicateAcronymException;
import com.example.carboncalculator.exceptions.InstitutionNotFoundException;
import com.example.carboncalculator.exceptions.InvalidStateException;
import com.example.carboncalculator.mappers.InstitutionMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.specifications.InstitutionSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InstitutionService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionService.class);

    private static final Set<String> VALID_STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final InstitutionRepository institutionRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Transactional(readOnly = true)
    public Page<InstitutionDTO> listForUser(AppUser user, String search, Pageable pageable) {
        Specification<Institution> spec = Specification.unrestricted();
        if (!user.isAdmin()) {
            spec = spec.and(InstitutionSpecification.hasActiveMember(user.getId()));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(InstitutionSpecification.nameOrAcronymContains(search.trim()));
        }

        return institutionRepository.findAllWithCounts(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<InstitutionDTO> getById(UUID id) {
        return institutionRepository.findById(id)
                .map(InstitutionMapper::toDTO);
    }

    @Transactional
    public InstitutionDTO create(CreateInstitutionRequest request) {
        validateState(request.state());
        validateAcronymNotDuplicate(request.acronym());

        Institution institution = institutionRepository.save(InstitutionMapper.toEntity(request));

        Laboratory laboratory = Laboratory.builder()
                .institution(institution)
                .name(request.laboratory().name())
                .build();
        laboratoryRepository.save(laboratory);

        log.info("Institution created: id={}, acronym={}", institution.getId(), institution.getAcronym());
        return InstitutionMapper.toDTO(institution);
    }

    @Transactional
    public InstitutionDTO update(UUID id, UpdateInstitutionRequest request) {
        Institution institution = institutionRepository.findById(id)
                .orElseThrow(() -> new InstitutionNotFoundException(id));

        validateState(request.state());
        if (!institution.getAcronym().equals(request.acronym())) {
            validateAcronymNotDuplicate(request.acronym());
        }

        institution.setName(request.name());
        institution.setAcronym(request.acronym());
        institution.setCity(request.city());
        institution.setState(request.state());

        institution = institutionRepository.save(institution);
        log.info("Institution updated: id={}, acronym={}", institution.getId(), institution.getAcronym());
        return InstitutionMapper.toDTO(institution);
    }

    private void validateState(String state) {
        if (state == null || !VALID_STATES.contains(state.toUpperCase())) {
            throw new InvalidStateException(state);
        }
    }

    private void validateAcronymNotDuplicate(String acronym) {
        if (institutionRepository.existsByAcronym(acronym)) {
            throw new DuplicateAcronymException(acronym);
        }
    }
}
