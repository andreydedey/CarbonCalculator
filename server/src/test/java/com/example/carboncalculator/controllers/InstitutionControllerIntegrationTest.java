package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.LaboratoryDTO;

/**
 * Testes de integração de instituição (US-001) contra um PostgreSQL real via
 * Testcontainers, subindo o contexto Spring completo (controller, service,
 * repository, Flyway). A tabela {@code institution} não tem RLS (ADR-004),
 * então aqui o foco é a criação atômica de instituição + primeiro
 * laboratório e as validações de sigla/UF — o isolamento por RLS é coberto
 * em {@link LaboratoryControllerIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InstitutionControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<InstitutionDTO> createInstitution(String acronym, String state, String labName) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Universidade Federal do Pará", acronym, "Belém", state, new CreateLaboratoryRequest(labName, null));
        return restTemplate.postForEntity("/institutions", request, InstitutionDTO.class);
    }

    private String uniqueAcronym(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    // @spec:AC-001 Instituição criada com dados válidos
    @Test
    void deveCriarInstituicaoELaboratorioVinculadoNumaUnicaOperacao() {
        ResponseEntity<InstitutionDTO> response = createInstitution(uniqueAcronym("UFPA"), "PA", "LABCOMP-01");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        InstitutionDTO institution = response.getBody();
        assertNotNull(institution);
        assertNotNull(institution.id());
        assertTrue(institution.active());
        assertEquals("PA", institution.state());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Institution-Id", institution.id().toString());
        ResponseEntity<LaboratoryDTO[]> laboratories = restTemplate.exchange(
                "/laboratories", HttpMethod.GET, new HttpEntity<>(headers), LaboratoryDTO[].class);

        assertEquals(HttpStatus.OK, laboratories.getStatusCode());
        assertTrue(Arrays.stream(laboratories.getBody()).anyMatch(lab -> lab.name().equals("LABCOMP-01")));
    }

    // @spec:AC-002 Sigla duplicada é rejeitada
    @Test
    void deveRecusarCriacaoQuandoSiglaJaExiste() {
        String acronym = uniqueAcronym("UFPA-DUP");
        assertEquals(HttpStatus.CREATED, createInstitution(acronym, "PA", "LABCOMP-01").getStatusCode());

        ResponseEntity<Map> duplicate = restTemplate.postForEntity("/institutions",
                new CreateInstitutionRequest("Outra Instituição", acronym, "Belém", "PA",
                        new CreateLaboratoryRequest("LAB-02", null)),
                Map.class);

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    // @spec:AC-003 UF inválida é rejeitada
    @Test
    void deveRecusarCriacaoQuandoUfNaoEstaEntreAs27UnidadesFederativas() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/institutions",
                new CreateInstitutionRequest("Instituição Teste", uniqueAcronym("IT"), "Cidade", "XX",
                        new CreateLaboratoryRequest("LAB-01", null)),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
