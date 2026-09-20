package com.example.carboncalculator.services;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.mappers.InstitutionMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

@Service
public class InstitutionService {

    private static final Set<String> VALID_STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final InstitutionRepository institutionRepository;
    private final LaboratoryRepository laboratoryRepository;

    public InstitutionService(InstitutionRepository institutionRepository, LaboratoryRepository laboratoryRepository) {
        this.institutionRepository = institutionRepository;
        this.laboratoryRepository = laboratoryRepository;
    }

    public List<InstitutionDTO> list() {
        return institutionRepository.findAll().stream()
                .map(InstitutionMapper::toDTO)
                .toList();
    }

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

    public static class DuplicateAcronymException extends RuntimeException {
        public DuplicateAcronymException(String acronym) {
            super("Já existe uma instituição com a sigla '" + acronym + "'");
        }
    }

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String state) {
            super("UF inválida: '" + state + "'");
        }
    }
}
