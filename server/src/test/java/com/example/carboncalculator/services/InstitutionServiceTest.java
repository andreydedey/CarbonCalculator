package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.exceptions.DuplicateAcronymException;
import com.example.carboncalculator.exceptions.InvalidStateException;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

class InstitutionServiceTest {

    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final UserInstitutionRepository membershipRepository = mock(UserInstitutionRepository.class);

    private InstitutionService service;

    @BeforeEach
    void setUp() {
        service = new InstitutionService(institutionRepository, laboratoryRepository, membershipRepository);
    }

    private CreateInstitutionRequest validRequest() {
        return new CreateInstitutionRequest("Universidade Federal do Pará", "UFPA", "Belém", "PA",
                new CreateLaboratoryRequest("LABCOMP-01"));
    }

    // @spec:AC-001 Instituição criada com dados válidos
    @Test
    void deveCriarInstituicaoELaboratorioVinculadoNumaUnicaOperacao() {
        when(institutionRepository.existsByAcronym("UFPA")).thenReturn(false);
        Institution savedInstitution = Institution.builder()
                .id(UUID.randomUUID())
                .name("Universidade Federal do Pará")
                .acronym("UFPA")
                .city("Belém")
                .state("PA")
                .active(true)
                .build();
        when(institutionRepository.save(any(Institution.class))).thenReturn(savedInstitution);
        when(laboratoryRepository.save(any(Laboratory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InstitutionDTO response = service.create(validRequest());

        assertEquals("UFPA", response.acronym());
        assertEquals("Universidade Federal do Pará", response.name());
        assertEquals("PA", response.state());

        verify(institutionRepository).save(any(Institution.class));
        verify(laboratoryRepository).save(any(Laboratory.class));
    }

    // @spec:AC-002 Sigla duplicada é rejeitada
    @Test
    void deveRecusarCriacaoQuandoSiglaJaExiste() {
        when(institutionRepository.existsByAcronym("UFPA")).thenReturn(true);

        assertThrows(DuplicateAcronymException.class, () -> service.create(validRequest()));

        verify(institutionRepository, never()).save(any(Institution.class));
        verify(laboratoryRepository, never()).save(any(Laboratory.class));
    }

    // @spec:AC-003 UF inválida é rejeitada
    @Test
    void deveRecusarCriacaoQuandoUfNaoEstaEntreAs27UnidadesFederativas() {
        CreateInstitutionRequest request = new CreateInstitutionRequest("Instituição Teste", "IT", "Cidade", "XX",
                new CreateLaboratoryRequest("LAB-01"));

        assertThrows(InvalidStateException.class, () -> service.create(request));

        verify(institutionRepository, never()).save(any(Institution.class));
        verify(laboratoryRepository, never()).save(any(Laboratory.class));
    }
}
