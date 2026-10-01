package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.repositories.LaboratoryRepository;

/**
 * Integration tests for Laboratory (US-002..US-005) against a real PostgreSQL
 * via Testcontainers with a non-superuser role to validate RLS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LaboratoryControllerIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "app";
    private static final String APP_PASSWORD = "app";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        createRestrictedApplicationRole();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1qd3Qtc2lnbmluZy1hdC1sZWFzdC0zMi1jaGFycw==");
    }

    private static void createRestrictedApplicationRole() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute("GRANT CREATE, USAGE ON SCHEMA public TO " + APP_ROLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to configure restricted test role", e);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoSpyBean
    private LaboratoryRepository laboratoryRepository;

    private String adminToken;

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            LoginRequest login = new LoginRequest("admin@admin.com", "password");
            ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity(
                    "/auth/login", login, AuthResponse.class);
            assertEquals(HttpStatus.OK, authResp.getStatusCode());
            adminToken = authResp.getBody().accessToken();
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private UUID createInstitutionAndReturnId(String acronymPrefix) {
        String acronym = acronymPrefix.substring(0, Math.min(acronymPrefix.length(), 4))
                + (System.nanoTime() % 1000000);
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronymPrefix, acronym, "Cidade", "PA");
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    private HttpHeaders headersFor(UUID institutionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private ResponseEntity<LaboratoryDTO> createLaboratory(UUID institutionId, String name) {
        return restTemplate.exchange("/laboratories", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryRequest(name, null), headersFor(institutionId)),
                LaboratoryDTO.class);
    }

    private ResponseEntity<String> listLaboratories(UUID institutionId, Boolean active) {
        String path = active == null ? "/laboratories?page=0&size=50"
                : "/laboratories?active=" + active + "&page=0&size=50";
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);
    }

    // @spec:AC-004 Laboratório criado com nome
    @Test
    void deveCriarLaboratorioInformandoApenasONome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<LaboratoryDTO> response = createLaboratory(institutionId, "LABCOMP-02");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("LABCOMP-02", response.getBody().name());
        assertTrue(response.getBody().active());

        ResponseEntity<String> list = listLaboratories(institutionId, null);
        assertTrue(list.getBody().contains("LABCOMP-02"));
    }

    // @spec:AC-005 Laboratório sem nome é rejeitado
    @Test
    void deveRecusarCriacaoDeLaboratorioSemNome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<Object> response = restTemplate.exchange("/laboratories", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryRequest("", null), headersFor(institutionId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
    @Test
    void deveListarApenasLaboratoriosAtivosPorPadrao() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryDTO inactive = createLaboratory(institutionId, "LABCOMP-INATIVO").getBody();
        restTemplate.exchange("/laboratories/" + inactive.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryDTO.class);

        ResponseEntity<String> list = listLaboratories(institutionId, true);

        assertFalse(list.getBody().contains(inactive.id().toString()));
    }

    // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
    @Test
    void deveIncluirLaboratoriosInativosQuandoSolicitado() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryDTO inactive = createLaboratory(institutionId, "LABCOMP-INATIVO").getBody();
        restTemplate.exchange("/laboratories/" + inactive.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryDTO.class);

        ResponseEntity<String> list = listLaboratories(institutionId, null);

        assertTrue(list.getBody().contains(inactive.id().toString()));
    }

    // @spec:AC-008 Isolamento por RLS entre instituições
    @Test
    void deveIsolarLaboratoriosPorInstituicaoViaRls() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        LaboratoryDTO labA = createLaboratory(institutionA, "LAB-A").getBody();
        LaboratoryDTO labB = createLaboratory(institutionB, "LAB-B").getBody();

        ResponseEntity<String> listFromA = listLaboratories(institutionA, null);

        assertTrue(listFromA.getBody().contains(labA.id().toString()));
        assertFalse(listFromA.getBody().contains(labB.id().toString()));
    }

    // @spec:AC-009 Requisição sem identificação de instituição é recusada
    @Test
    void deveRecusarRequisicaoSemHeaderXInstitutionId() {
        ResponseEntity<Object> response = restTemplate.exchange(
                "/laboratories?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-010 Acesso direto a laboratório de outra instituição é negado
    @Test
    void deveNegarAcessoDiretoALaboratorioDeOutraInstituicaoComo404() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        LaboratoryDTO labA = createLaboratory(institutionA, "LAB-A").getBody();

        ResponseEntity<Object> response = restTemplate.exchange(
                "/laboratories/" + labA.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionB)), Object.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // @spec:AC-011 Desativação preserva o laboratório
    @Test
    void deveDesativarLaboratorioPreservandoSeusDados() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryDTO created = createLaboratory(institutionId, "LABCOMP-03").getBody();

        ResponseEntity<LaboratoryDTO> deactivateResponse = restTemplate.exchange(
                "/laboratories/" + created.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryDTO.class);

        assertEquals(HttpStatus.OK, deactivateResponse.getStatusCode());
        LaboratoryDTO deactivated = deactivateResponse.getBody();
        assertFalse(deactivated.active());
        assertEquals(created.id(), deactivated.id());
        assertEquals(created.name(), deactivated.name());

        ResponseEntity<String> list = listLaboratories(institutionId, null);
        assertTrue(list.getBody().contains(created.id().toString()));
    }

    // @spec:AC-012 Exclusão bloqueada quando há dependentes
    @Test
    void deveBloquearExclusaoQuandoLaboratorioPossuiDependentes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryDTO created = createLaboratory(institutionId, "LABCOMP-COM-DEPENDENTE").getBody();

        when(laboratoryRepository.existsDependentsByLaboratoryId(created.id())).thenReturn(true);

        ResponseEntity<Object> response = restTemplate.exchange("/laboratories/" + created.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(institutionId)), Object.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-013 Exclusão permitida quando não há dependentes
    @Test
    void devePermitirExclusaoQuandoLaboratorioNaoPossuiDependentes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryDTO created = createLaboratory(institutionId, "LABCOMP-SEM-DEPENDENTE").getBody();

        ResponseEntity<Void> response = restTemplate.exchange("/laboratories/" + created.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<String> list = listLaboratories(institutionId, null);
        assertFalse(list.getBody().contains(created.id().toString()));
    }
}
