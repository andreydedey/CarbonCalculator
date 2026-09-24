package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.exceptions.LaboratoryHasDependentsException;
import com.example.carboncalculator.exceptions.MissingLaboratoryNameException;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

class LaboratoryServiceTest {

    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository = mock(LaboratoryEquipmentRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);

    private LaboratoryService service;

    @BeforeEach
    void setUp() {
        service = new LaboratoryService(laboratoryRepository, laboratoryEquipmentRepository, institutionRepository);
        TenantContext.setInstitutionId("550e8400-e29b-41d4-a716-446655440000");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // @spec:AC-004 Laboratório criado com nome
    @Test
    void deveCriarLaboratorioComNomeNoContextoDaInstituicaoAtiva() {
        Institution institution = Institution.builder().id(UUID.fromString(TenantContext.getInstitutionId())).build();
        when(institutionRepository.getReferenceById(institution.getId())).thenReturn(institution);
        when(laboratoryRepository.save(any(Laboratory.class))).thenAnswer(invocation -> {
            Laboratory laboratory = invocation.getArgument(0);
            laboratory.setId(UUID.randomUUID());
            return laboratory;
        });

        LaboratoryDTO response = service.create(new CreateLaboratoryRequest("LABCOMP-02", null));

        assertEquals("LABCOMP-02", response.name());
        assertTrue(response.active());
        verify(laboratoryRepository).save(any(Laboratory.class));
    }

    // @spec:AC-005 Laboratório sem nome é rejeitado
    @Test
    void deveRecusarCriacaoDeLaboratorioSemNome() {
        assertThrows(MissingLaboratoryNameException.class,
                () -> service.create(new CreateLaboratoryRequest(" ", null)));

        verify(laboratoryRepository, never()).save(any(Laboratory.class));
    }

    // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
    @Test
    void deveListarApenasLaboratoriosAtivosPorPadrao() {
        Laboratory ativo = Laboratory.builder().id(UUID.randomUUID()).name("Ativo").active(true).build();
        when(laboratoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ativo)));

        Page<LaboratoryDTO> result = service.list(true, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).active());
        CriteriaBuilder cb = applyCapturedSpecification();
        verify(cb).isTrue(any());
    }

    // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
    @Test
    void deveIncluirLaboratoriosInativosQuandoSolicitado() {
        Laboratory ativo = Laboratory.builder().id(UUID.randomUUID()).name("Ativo").active(true).build();
        Laboratory inativo = Laboratory.builder().id(UUID.randomUUID()).name("Inativo").active(false).build();
        when(laboratoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ativo, inativo)));

        Page<LaboratoryDTO> result = service.list(null, null, PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(lab -> !lab.active()));
        CriteriaBuilder cb = applyCapturedSpecification();
        verify(cb, never()).isTrue(any());
    }

    // Evaluates the Specification passed to the repository against mocks,
    // so tests can assert whether the "active" filter was applied.
    @SuppressWarnings("unchecked")
    private CriteriaBuilder applyCapturedSpecification() {
        ArgumentCaptor<Specification<Laboratory>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(laboratoryRepository).findAll(captor.capture(), any(Pageable.class));

        Root<Laboratory> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        captor.getValue().toPredicate(root, query, cb);
        return cb;
    }

    // @spec:AC-011 Desativação preserva o laboratório
    @Test
    void deveDesativarLaboratorioSemRemoveLo() {
        UUID id = UUID.randomUUID();
        Laboratory laboratory = Laboratory.builder().id(id).name("LABCOMP-01").active(true).build();
        when(laboratoryRepository.findById(id)).thenReturn(Optional.of(laboratory));
        when(laboratoryRepository.save(any(Laboratory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LaboratoryDTO response = service.deactivate(id);

        assertFalse(response.active());
        verify(laboratoryRepository, never()).delete(any(Laboratory.class));
        verify(laboratoryRepository).save(laboratory);
    }

    // @spec:AC-012 Exclusão bloqueada quando há dependentes
    @Test
    void deveBloquearExclusaoQuandoLaboratorioPossuiDependentes() {
        UUID id = UUID.randomUUID();
        Laboratory laboratory = Laboratory.builder().id(id).name("LABCOMP-01").active(true).build();
        when(laboratoryRepository.findById(id)).thenReturn(Optional.of(laboratory));
        when(laboratoryRepository.existsDependentsByLaboratoryId(id)).thenReturn(true);

        assertThrows(LaboratoryHasDependentsException.class, () -> service.delete(id));

        verify(laboratoryRepository, never()).delete(any(Laboratory.class));
    }

    // @spec:AC-013 Exclusão permitida quando não há dependentes
    @Test
    void devePermitirExclusaoQuandoLaboratorioNaoPossuiDependentes() {
        UUID id = UUID.randomUUID();
        Laboratory laboratory = Laboratory.builder().id(id).name("LABCOMP-01").active(true).build();
        when(laboratoryRepository.findById(id)).thenReturn(Optional.of(laboratory));
        when(laboratoryRepository.existsDependentsByLaboratoryId(id)).thenReturn(false);

        service.delete(id);

        verify(laboratoryRepository).delete(laboratory);
    }
}
