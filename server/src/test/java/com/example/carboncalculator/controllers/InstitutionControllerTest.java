package com.example.carboncalculator.controllers;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.services.InstitutionService;

/**
 * Testa o roteamento HTTP do {@link InstitutionController} isoladamente, com
 * o {@link InstitutionService} mockado. A instituição não exige o header
 * {@code X-Institution-Id} (ver TenantFilter), por isso o teste não precisa
 * registrá-lo no MockMvc.
 */
class InstitutionControllerTest {

    private final InstitutionService institutionService = mock(InstitutionService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InstitutionController(institutionService)).build();

    // @spec:AC-001 Instituição criada com dados válidos
    @Test
    void deveCriarInstituicaoComLaboratorioVinculadoNumaUnicaRequisicao() throws Exception {
        InstitutionResponse response = new InstitutionResponse(
                UUID.randomUUID(), "Universidade Federal do Pará", "UFPA", "Belém", "PA", true, OffsetDateTime.now());
        when(institutionService.create(any(CreateInstitutionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Universidade Federal do Pará",
                                  "acronym": "UFPA",
                                  "city": "Belém",
                                  "state": "PA",
                                  "laboratory": { "name": "LABCOMP-01" }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acronym", is("UFPA")))
                .andExpect(jsonPath("$.state", is("PA")))
                .andExpect(jsonPath("$.active", is(true)));
    }
}
