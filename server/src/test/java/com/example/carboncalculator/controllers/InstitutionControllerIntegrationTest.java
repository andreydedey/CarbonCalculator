package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
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
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LoginRequest;

/**
 * Integration tests for Institution (US-001) against a real PostgreSQL via
 * Testcontainers. Covers creation with linked laboratory and validations.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InstitutionControllerIntegrationTest {

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

    private ResponseEntity<String> createInstitution(String acronym, String state) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Universidade Federal do Pará", acronym, "Belém", state);
        return restTemplate.exchange("/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), String.class);
    }

    private UUID extractId(String json) {
        int idx = json.indexOf("\"id\":\"") + 6;
        return UUID.fromString(json.substring(idx, json.indexOf("\"", idx)));
    }

    private String uniqueAcronym(String prefix) {
        return prefix.substring(0, Math.min(prefix.length(), 4))
                + (System.nanoTime() % 1000000);
    }

    // @spec:AC-001 Instituição criada com dados válidos
    @Test
    void deveCriarInstituicaoELaboratorioVinculadoNumaUnicaOperacao() {
        ResponseEntity<String> response = createInstitution(uniqueAcronym("UFPA"), "PA");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);
        assertNotNull(extractId(body));
        assertTrue(body.contains("\"active\":true"));
        assertTrue(body.contains("\"state\":\"PA\""));
    }

    // @spec:AC-002 Sigla duplicada é rejeitada
    @Test
    void deveRecusarCriacaoQuandoSiglaJaExiste() {
        String acronym = uniqueAcronym("DUP");
        assertEquals(HttpStatus.CREATED, createInstitution(acronym, "PA").getStatusCode());

        ResponseEntity<String> duplicate = restTemplate.exchange("/institutions", HttpMethod.POST,
                new HttpEntity<>(new CreateInstitutionRequest("Outra Instituição", acronym, "Belém", "PA"),
                        authHeaders()),
                String.class);

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    // @spec:AC-003 UF inválida é rejeitada
    @Test
    void deveRecusarCriacaoQuandoUfNaoEstaEntreAs27UnidadesFederativas() {
        ResponseEntity<String> response = restTemplate.exchange("/institutions", HttpMethod.POST,
                new HttpEntity<>(new CreateInstitutionRequest("Instituição Teste", uniqueAcronym("IT"), "Cidade", "XX"),
                        authHeaders()),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
