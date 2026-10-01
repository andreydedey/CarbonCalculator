package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.dto.LoginRequest;

/**
 * Integration tests for EquipmentModel (US-012..US-014) against a real
 * PostgreSQL via Testcontainers with a non-superuser role to validate RLS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EquipmentModelControllerIntegrationTest {

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

    private HttpHeaders headersFor(UUID institutionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private UUID createInstitutionAndReturnId(String prefix) {
        String acronym = prefix.substring(0, Math.min(prefix.length(), 4)) + (System.nanoTime() % 1000000);
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + prefix, acronym, "Cidade", "PA");
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    private ResponseEntity<EquipmentModelDTO> createModel(UUID institutionId, String name) {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                name, "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);
        return restTemplate.exchange("/equipment-models", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), EquipmentModelDTO.class);
    }

    private UUID createModelAndReturnId(UUID institutionId, String name) {
        ResponseEntity<EquipmentModelDTO> response = createModel(institutionId, name);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private UUID createConfigurationForModel(UUID institutionId, UUID modelId, String os) {
        CreateConfigurationRequest request = new CreateConfigurationRequest(modelId, os, null);
        ResponseEntity<String> response = restTemplate.exchange("/configurations", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    // @spec:AC-034
    @Test
    void deveCriarModeloDeEquipamentoComDadosValidos() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<EquipmentModelDTO> response = createModel(institutionId, "Dell OptiPlex 3080");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        EquipmentModelDTO body = response.getBody();
        assertNotNull(body.id());
        assertEquals("Dell OptiPlex 3080", body.name());
        assertEquals("Intel Core i5", body.processor());
        assertEquals(16, body.memoryGb());

        ResponseEntity<String> list = restTemplate.exchange(
                "/equipment-models?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);
        assertTrue(list.getBody().contains("Dell OptiPlex 3080"));
    }

    // @spec:AC-035
    @Test
    void deveRecusarCriacaoDeModeloSemNome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                "", "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);
        ResponseEntity<Object> response = restTemplate.exchange("/equipment-models", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-036
    @Test
    void deveCriarModeloSemMonitorComSucesso() {
        // With the current architecture, monitors are separate entities linked via
        // Configuration. Creating an EquipmentModel never requires monitor data.
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                "Lenovo ThinkCentre M720", "desktop", "Intel Core i7", 95, 8, 32, null, null, false, null);
        ResponseEntity<EquipmentModelDTO> response = restTemplate.exchange(
                "/equipment-models", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), EquipmentModelDTO.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().id());
        assertFalse(response.getBody().hasIntegratedScreen());
    }

    // @spec:AC-037
    @Test
    void deveListarModelosPaginadosOrdenadosPorNome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        createModelAndReturnId(institutionId, "ZZZ Model");
        createModelAndReturnId(institutionId, "AAA Model");

        ResponseEntity<String> response = restTemplate.exchange(
                "/equipment-models?page=0&size=50&sort=name,asc", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("AAA Model"));
        assertTrue(body.contains("ZZZ Model"));
        assertTrue(body.indexOf("AAA Model") < body.indexOf("ZZZ Model"));
    }

    // @spec:AC-038
    @Test
    void deveBuscarModelosPorNomeCaseInsensitive() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        createModelAndReturnId(institutionId, "Dell XPS Desktop");
        createModelAndReturnId(institutionId, "HP EliteDesk 800");

        ResponseEntity<String> response = restTemplate.exchange(
                "/equipment-models?name=dell&page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Dell XPS Desktop"));
        assertFalse(response.getBody().contains("HP EliteDesk 800"));
    }

    // @spec:AC-039
    @Test
    void deveIsolarModelosEntreInstituicoesPorRls() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        UUID modelAId = createModelAndReturnId(institutionA, "Model from A");
        createModelAndReturnId(institutionB, "Model from B");

        ResponseEntity<String> listFromA = restTemplate.exchange(
                "/equipment-models?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionA)), String.class);

        assertTrue(listFromA.getBody().contains("Model from A"));
        assertFalse(listFromA.getBody().contains("Model from B"));

        // Direct access to other institution's model is also rejected via RLS
        ResponseEntity<Object> directAccess = restTemplate.exchange(
                "/equipment-models/" + modelAId, HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionB)), Object.class);
        assertEquals(HttpStatus.NOT_FOUND, directAccess.getStatusCode());
    }

    // @spec:AC-040
    @Test
    void deveEditarModeloDeEquipamento() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID modelId = createModelAndReturnId(institutionId, "HP 800 G6");

        CreateEquipmentModelRequest update = new CreateEquipmentModelRequest(
                "HP 800 G6 Updated", "desktop", "Intel Core i9", 125, 12, 64, null, null, false, "Workstation");
        ResponseEntity<EquipmentModelDTO> response = restTemplate.exchange(
                "/equipment-models/" + modelId, HttpMethod.PUT,
                new HttpEntity<>(update, headersFor(institutionId)), EquipmentModelDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("HP 800 G6 Updated", response.getBody().name());
        assertEquals(64, response.getBody().memoryGb());
    }

    // @spec:AC-041
    @Test
    void devePermitirExclusaoDeModeloSemConfiguracoes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID modelId = createModelAndReturnId(institutionId, "Model to Delete");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/equipment-models/" + modelId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<String> list = restTemplate.exchange(
                "/equipment-models?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);
        assertFalse(list.getBody().contains("Model to Delete"));
    }

    // @spec:AC-042
    @Test
    void deveBloquearExclusaoDeModeloVinculadoAConfiguracao() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID modelId = createModelAndReturnId(institutionId, "Model in Use");
        createConfigurationForModel(institutionId, modelId, "Linux");

        ResponseEntity<Object> response = restTemplate.exchange(
                "/equipment-models/" + modelId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId)), Object.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }
}
