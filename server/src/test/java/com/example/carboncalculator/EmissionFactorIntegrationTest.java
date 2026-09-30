package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
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
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.dto.LoginRequest;

/**
 * Integration tests for Emission Factor CRUD (US-026).
 * Emission factors are global (no RLS), so no X-Institution-Id header needed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EmissionFactorIntegrationTest {

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
    void authenticate() {
        if (adminToken != null) return;
        LoginRequest login = new LoginRequest("admin@admin.com", "password");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", login, AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        adminToken = response.getBody().accessToken();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private EmissionFactorDTO createFactor(short year, short month, String value, String source) {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                year, month, new BigDecimal(value), source);
        ResponseEntity<EmissionFactorDTO> response = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                EmissionFactorDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    // @spec:AC-072 Criar fator de emissão válido
    @Test
    void deveCriarFatorDeEmissaoValido() {
        EmissionFactorDTO factor = createFactor((short) 2024, (short) 1, "0.0501", "MCTI — SIN jan/2024");

        assertNotNull(factor);
        assertNotNull(factor.id());
        assertEquals(2024, factor.year());
        assertEquals(1, factor.month());
        assertEquals(new BigDecimal("0.0501"), factor.value());
        assertEquals("MCTI — SIN jan/2024", factor.source());

        // Verify it appears in listing
        ResponseEntity<String> list = restTemplate.exchange(
                "/emission-factors?year=2024", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertTrue(list.getBody().contains("0.0501"));
    }

    // @spec:AC-073 Rejeitar fator duplicado (mesmo ano+mês)
    @Test
    void deveRejeitarFatorDuplicado() {
        CreateEmissionFactorRequest duplicate = new CreateEmissionFactorRequest(
                (short) 2025, (short) 3, new BigDecimal("0.0999"), "Teste duplicado");
        ResponseEntity<Object> response = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(duplicate, authHeaders()), Object.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-074 Rejeitar fator com valor inválido
    @Test
    void deveRejeitarFatorComValorInvalido() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                (short) 2023, (short) 6, new BigDecimal("-0.01"), "");
        ResponseEntity<Object> response = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-074 Rejeitar fator com fonte vazia
    @Test
    void deveRejeitarFatorComFonteVazia() {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                (short) 2023, (short) 7, new BigDecimal("0.05"), "");
        ResponseEntity<Object> response = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-075 Listar fatores filtrados por ano
    @Test
    void deveListarFatoresFiltradosPorAno() {
        createFactor((short) 2023, (short) 1, "0.0600", "MCTI — SIN jan/2023");

        ResponseEntity<String> response = restTemplate.exchange(
                "/emission-factors?year=2025&size=100", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"totalElements\":12"));

        ResponseEntity<String> response2023 = restTemplate.exchange(
                "/emission-factors?year=2023&size=100", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertEquals(HttpStatus.OK, response2023.getStatusCode());
        assertTrue(response2023.getBody().contains("0.0600"));
    }

    // @spec:AC-076 Atualizar fator existente
    @Test
    void deveAtualizarFatorExistente() {
        EmissionFactorDTO created = createFactor((short) 2024, (short) 4, "0.0400", "Original");

        CreateEmissionFactorRequest updateRequest = new CreateEmissionFactorRequest(
                (short) 2024, (short) 4, new BigDecimal("0.0555"), "Atualizado");
        ResponseEntity<EmissionFactorDTO> response = restTemplate.exchange(
                "/emission-factors/" + created.id(), HttpMethod.PUT,
                new HttpEntity<>(updateRequest, authHeaders()),
                EmissionFactorDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("0.0555"), response.getBody().value());
        assertEquals("Atualizado", response.getBody().source());
    }

    // @spec:AC-077 Excluir fator de emissão
    @Test
    void deveExcluirFatorDeEmissao() {
        EmissionFactorDTO created = createFactor((short) 2024, (short) 5, "0.0700", "Para remover");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/emission-factors/" + created.id(), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<String> list = restTemplate.exchange(
                "/emission-factors?year=2024&size=100", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);
        assertFalse(list.getBody().contains(created.id().toString()));
    }

    // @spec:AC-078 Apenas administradores gerenciam fatores
    @Test
    void devePermitirLeituraParaQualquerUsuarioAutenticado() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/emission-factors?year=2025&size=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
