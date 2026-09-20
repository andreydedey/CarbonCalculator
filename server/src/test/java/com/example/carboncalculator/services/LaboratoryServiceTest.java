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

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

class LaboratoryServiceTest {

    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);

    private LaboratoryService service;

    @BeforeEach
    void setUp() {
        service = new LaboratoryService(laboratoryRepository, institutionRepository);
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

        LaboratoryDTO response = service.create(new CreateLaboratoryRequest("LABCOMP-02"));

        assertEquals("LABCOMP-02", response.name());
        assertTrue(response.active());
        verify(laboratoryRepository).save(any(Laboratory.class));
    }

    // @spec:AC-005 Laboratório sem nome é rejeitado
    @Test
    void deveRecusarCriacaoDeLaboratorioSemNome() {
        assertThrows(LaboratoryService.MissingLaboratoryNameException.class,
                () -> service.create(new CreateLaboratoryRequest(" ")));

        verify(laboratoryRepository, never()).save(any(Laboratory.class));
    }

    // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
    @Test
    void deveListarApenasLaboratoriosAtivosPorPadrao() {
        Laboratory ativo = Laboratory.builder().id(UUID.randomUUID()).name("Ativo").active(true).build();
        when(laboratoryRepository.findByActiveTrue()).thenReturn(List.of(ativo));

        List<LaboratoryDTO> result = service.list(false);

        assertEquals(1, result.size());
        assertTrue(result.get(0).active());
        verify(laboratoryRepository, never()).findAll();
    }

    // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
    @Test
    void deveIncluirLaboratoriosInativosQuandoSolicitado() {
        Laboratory ativo = Laboratory.builder().id(UUID.randomUUID()).name("Ativo").active(true).build();
        Laboratory inativo = Laboratory.builder().id(UUID.randomUUID()).name("Inativo").active(false).build();
        when(laboratoryRepository.findAll()).thenReturn(List.of(ativo, inativo));

        List<LaboratoryDTO> result = service.list(true);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(lab -> !lab.active()));
        verify(laboratoryRepository, never()).findByActiveTrue();
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

        assertThrows(LaboratoryService.LaboratoryHasDependentsException.class, () -> service.delete(id));

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
