package com.example.carboncalculator.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryResponse;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.mappers.LaboratoryMapper;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

/**
 * Regras de negócio de laboratório (US-002, US-003, US-005). O isolamento
 * por instituição é garantido pelo RLS (ADR-004) — este service não filtra
 * manualmente por institutionId nas consultas, apenas resolve a instituição
 * ativa (via {@link TenantContext}) ao criar um novo laboratório.
 */
@Service
public class LaboratoryService {

    private final LaboratoryRepository laboratoryRepository;
    private final InstitutionRepository institutionRepository;

    public LaboratoryService(LaboratoryRepository laboratoryRepository, InstitutionRepository institutionRepository) {
        this.laboratoryRepository = laboratoryRepository;
        this.institutionRepository = institutionRepository;
    }

    @Transactional
    public LaboratoryResponse create(CreateLaboratoryRequest request) {
        validateName(request.name());

        Institution institution = institutionRepository.getReferenceById(currentInstitutionId());
        Laboratory laboratory = Laboratory.builder()
                .institution(institution)
                .name(request.name())
                .build();

        return LaboratoryMapper.toResponse(laboratoryRepository.save(laboratory));
    }

    public List<LaboratoryResponse> list(boolean includeInactive) {
        List<Laboratory> laboratories = includeInactive
                ? laboratoryRepository.findAll()
                : laboratoryRepository.findByActiveTrue();

        return laboratories.stream().map(LaboratoryMapper::toResponse).toList();
    }

    @Transactional
    public LaboratoryResponse deactivate(UUID id) {
        Laboratory laboratory = getOrThrow(id);
        laboratory.setActive(false);
        return LaboratoryMapper.toResponse(laboratoryRepository.save(laboratory));
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

    /**
     * Sinaliza que o nome do laboratório, campo obrigatório, não foi
     * informado. O controller (T-007) deve traduzir isto para 400 Bad Request.
     */
    public static class MissingLaboratoryNameException extends RuntimeException {
        public MissingLaboratoryNameException() {
            super("O nome do laboratório é obrigatório");
        }
    }

    /**
     * Sinaliza que o laboratório não foi encontrado (ou pertence a outra
     * instituição, escondido pelo RLS). O controller (T-007) deve traduzir
     * isto para 404 Not Found.
     */
    public static class LaboratoryNotFoundException extends RuntimeException {
        public LaboratoryNotFoundException(UUID id) {
            super("Laboratório não encontrado: " + id);
        }
    }

    /**
     * Sinaliza que o laboratório possui equipamentos ou medições vinculadas
     * e por isso não pode ser excluído — apenas desativado. O controller
     * (T-007) deve traduzir isto para 409 Conflict.
     */
    public static class LaboratoryHasDependentsException extends RuntimeException {
        public LaboratoryHasDependentsException(UUID id) {
            super("Laboratório " + id + " possui registros dependentes; desative-o em vez de excluir");
        }
    }
}
