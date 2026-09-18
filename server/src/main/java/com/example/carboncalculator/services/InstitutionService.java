package com.example.carboncalculator.services;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.mappers.InstitutionMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

/**
 * Regras de negócio de instituição (US-001). A criação inclui o primeiro
 * laboratório vinculado numa única transação — ver TDD, seção "Decisões de
 * modelagem".
 */
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

    @Transactional
    public InstitutionResponse create(CreateInstitutionRequest request) {
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

    /**
     * Sinaliza que a sigla informada já está em uso por outra instituição.
     * O controller (T-007) deve traduzir isto para 409 Conflict.
     */
    public static class DuplicateAcronymException extends RuntimeException {
        public DuplicateAcronymException(String acronym) {
            super("Já existe uma instituição com a sigla '" + acronym + "'");
        }
    }

    /**
     * Sinaliza que a UF informada não está entre as 27 unidades federativas.
     * O controller (T-007) deve traduzir isto para 400 Bad Request.
     */
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String state) {
            super("UF inválida: '" + state + "'");
        }
    }
}
