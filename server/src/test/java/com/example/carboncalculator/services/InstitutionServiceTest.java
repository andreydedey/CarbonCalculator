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
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.exceptions.DuplicateAcronymException;
import com.example.carboncalculator.exceptions.InvalidStateException;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

class InstitutionServiceTest {

    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final UserInstitutionRepository membershipRepository = mock(UserInstitutionRepository.class);

    private InstitutionService service;
    private final AppUser admin = AppUser.builder().id(UUID.randomUUID()).email("admin@test.com").admin(true).build();

    @BeforeEach
    void setUp() {
        service = new InstitutionService(institutionRepository, membershipRepository);
    }

    private CreateInstitutionRequest validRequest() {
        return new CreateInstitutionRequest("Universidade Federal do Pará", "UFPA", "Belém", "PA");
    }

    // @spec:AC-001 Instituição criada com dados válidos
    @Test
    void deveCriarInstituicaoComDadosValidos() {
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

        InstitutionDTO response = service.create(validRequest(), admin);

        assertEquals("UFPA", response.acronym());
        assertEquals("Universidade Federal do Pará", response.name());
        assertEquals("PA", response.state());

        verify(institutionRepository).save(any(Institution.class));
    }

    // @spec:AC-002 Sigla duplicada é rejeitada
    @Test
    void deveRecusarCriacaoQuandoSiglaJaExiste() {
        when(institutionRepository.existsByAcronym("UFPA")).thenReturn(true);

        assertThrows(DuplicateAcronymException.class, () -> service.create(validRequest(), admin));

        verify(institutionRepository, never()).save(any(Institution.class));
    }

    // @spec:AC-003 UF inválida é rejeitada
    @Test
    void deveRecusarCriacaoQuandoUfNaoEstaEntreAs27UnidadesFederativas() {
        CreateInstitutionRequest request = new CreateInstitutionRequest("Instituição Teste", "IT", "Cidade", "XX");

        assertThrows(InvalidStateException.class, () -> service.create(request, admin));

        verify(institutionRepository, never()).save(any(Institution.class));
    }
}
