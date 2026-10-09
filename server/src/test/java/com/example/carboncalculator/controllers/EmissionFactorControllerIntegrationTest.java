package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
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
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.PageResponse;
import com.example.carboncalculator.dto.AcceptInviteRequest;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.UserMemberDTO;

/**
 * Integration tests for Emission Factor (US-033..US-034) against a real
 * PostgreSQL via Testcontainers with a non-superuser role to validate RLS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EmissionFactorControllerIntegrationTest {

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
        return headersFor(institutionId, adminToken);
    }

    private HttpHeaders headersFor(UUID institutionId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(token);
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

    /** Invites a new user as MANAGER of the given institution and accepts the invite, returning its token. */
    private String createManagerTokenFor(UUID institutionId) {
        String email = "manager" + System.nanoTime() + "@example.com";

        // Invite via API as admin
        InviteRequest invite = new InviteRequest(email, "MANAGER");
        HttpHeaders inviteHeaders = authHeaders();
        inviteHeaders.set("X-Institution-Id", institutionId.toString());
        ResponseEntity<UserMemberDTO> inviteResp = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(invite, inviteHeaders), UserMemberDTO.class);
        assertEquals(HttpStatus.CREATED, inviteResp.getStatusCode());

        String rawToken = inviteResp.getBody().inviteLink().replace("/register?token=", "");

        // Accept invite to create the user
        AcceptInviteRequest accept = new AcceptInviteRequest("Gestor Teste", "password123");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", accept, AuthResponse.class, rawToken);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        return response.getBody().accessToken();
    }

    private ResponseEntity<EmissionFactorDTO> createFactor(
            UUID institutionId, YearMonth referenceMonth, BigDecimal value, String source) {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(referenceMonth, value, source);
        return restTemplate.exchange("/emission-factors", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), EmissionFactorDTO.class);
    }

    private UUID createFactorAndReturnId(
            UUID institutionId, YearMonth referenceMonth, BigDecimal value, String source) {
        ResponseEntity<EmissionFactorDTO> response = createFactor(institutionId, referenceMonth, value, source);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private ResponseEntity<PageResponse<EmissionFactorDTO>> listFactors(UUID institutionId, Integer year) {
        String path = year == null ? "/emission-factors" : "/emission-factors?year=" + year;
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)),
                new ParameterizedTypeReference<>() {});
    }

    // @spec:AC-103
    @Test
    void deveCriarFatorComDadosValidos() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<EmissionFactorDTO> response = createFactor(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        EmissionFactorDTO body = response.getBody();
        assertNotNull(body.id());
        assertEquals(YearMonth.of(2025, 6), body.referenceMonth());
        assertEquals(new BigDecimal("0.074"), body.value());
        assertEquals("MCTI", body.source());
    }

    // @spec:AC-104
    @Test
    void deveRecusarFatorSemMesDeReferencia() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<EmissionFactorDTO> response = createFactor(
                institutionId, null, new BigDecimal("0.074"), "MCTI");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-105
    @Test
    void deveRecusarFatorComValorZeroOuNegativo() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<EmissionFactorDTO> zeroResponse = createFactor(
                institutionId, YearMonth.of(2025, 6), BigDecimal.ZERO, "MCTI");
        assertEquals(HttpStatus.BAD_REQUEST, zeroResponse.getStatusCode());

        ResponseEntity<EmissionFactorDTO> negativeResponse = createFactor(
                institutionId, YearMonth.of(2025, 7), new BigDecimal("-0.5"), "MCTI");
        assertEquals(HttpStatus.BAD_REQUEST, negativeResponse.getStatusCode());
    }

    // @spec:AC-106
    @Test
    void deveRecusarFatorSemFonte() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<EmissionFactorDTO> response = createFactor(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "  ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-107
    @Test
    void deveBloquearDuplicataDoMesmoMes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        ResponseEntity<EmissionFactorDTO> response = createFactor(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.08"), "MCTI");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-108
    @Test
    void deveAtualizarFatorComSucesso() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID factorId = createFactorAndReturnId(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        CreateEmissionFactorRequest update = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), new BigDecimal("0.08"), "MCTI (revisado)");
        ResponseEntity<EmissionFactorDTO> response = restTemplate.exchange(
                "/emission-factors/" + factorId, HttpMethod.PUT,
                new HttpEntity<>(update, headersFor(institutionId)), EmissionFactorDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("0.08"), response.getBody().value());
        assertEquals("MCTI (revisado)", response.getBody().source());
    }

    // @spec:AC-109
    @Test
    void deveBloquearAtualizacaoParaMesJaOcupadoPorOutroFator() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID mayFactorId = createFactorAndReturnId(
                institutionId, YearMonth.of(2025, 5), new BigDecimal("0.07"), "MCTI");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        CreateEmissionFactorRequest update = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), new BigDecimal("0.07"), "MCTI");
        ResponseEntity<EmissionFactorDTO> response = restTemplate.exchange(
                "/emission-factors/" + mayFactorId, HttpMethod.PUT,
                new HttpEntity<>(update, headersFor(institutionId)), EmissionFactorDTO.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-110
    @Test
    void deveRemoverFatorComSucesso() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID factorId = createFactorAndReturnId(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/emission-factors/" + factorId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<PageResponse<EmissionFactorDTO>> list = listFactors(institutionId, null);
        assertTrue(list.getBody().content().stream().noneMatch(f -> f.id().equals(factorId)));
    }

    // @spec:AC-111
    @Test
    void deveRetornar404AoRemoverFatorInexistente() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/emission-factors/" + UUID.randomUUID(), HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // @spec:AC-112
    @Test
    void deveListarFatoresEmOrdemDecrescenteDeCompetencia() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 1), new BigDecimal("0.07"), "MCTI");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 3), new BigDecimal("0.072"), "MCTI");

        ResponseEntity<PageResponse<EmissionFactorDTO>> response = listFactors(institutionId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var months = response.getBody().content().stream().map(EmissionFactorDTO::referenceMonth).toList();
        assertEquals(3, months.size());
        assertEquals(YearMonth.of(2025, 6), months.get(0));
        assertEquals(YearMonth.of(2025, 3), months.get(1));
        assertEquals(YearMonth.of(2025, 1), months.get(2));
    }

    // @spec:AC-113
    @Test
    void deveFiltrarPorAnoRetornandoApenasFatoresDaqueleAno() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        createFactorAndReturnId(institutionId, YearMonth.of(2024, 12), new BigDecimal("0.06"), "MCTI");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 1), new BigDecimal("0.07"), "MCTI");
        createFactorAndReturnId(institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");

        ResponseEntity<PageResponse<EmissionFactorDTO>> response = listFactors(institutionId, 2025);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var months = response.getBody().content().stream().map(EmissionFactorDTO::referenceMonth).toList();
        assertEquals(2, months.size());
        assertTrue(months.stream().allMatch(m -> m.getYear() == 2025));
    }

    // @spec:AC-114
    @Test
    void managerNaoPodeCriarAtualizarNemRemoverFatores() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        UUID factorId = createFactorAndReturnId(
                institutionId, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");
        String managerToken = createManagerTokenFor(institutionId);

        CreateEmissionFactorRequest createRequest = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 7), new BigDecimal("0.08"), "MCTI");
        ResponseEntity<EmissionFactorDTO> createResponse = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(createRequest, headersFor(institutionId, managerToken)), EmissionFactorDTO.class);
        assertEquals(HttpStatus.FORBIDDEN, createResponse.getStatusCode());

        CreateEmissionFactorRequest updateRequest = new CreateEmissionFactorRequest(
                YearMonth.of(2025, 6), new BigDecimal("0.09"), "MCTI");
        ResponseEntity<EmissionFactorDTO> updateResponse = restTemplate.exchange(
                "/emission-factors/" + factorId, HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headersFor(institutionId, managerToken)), EmissionFactorDTO.class);
        assertEquals(HttpStatus.FORBIDDEN, updateResponse.getStatusCode());

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/emission-factors/" + factorId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId, managerToken)), Void.class);
        assertEquals(HttpStatus.FORBIDDEN, deleteResponse.getStatusCode());
    }

    // @spec:AC-115
    @Test
    void deveIsolarFatoresEntreInstituicoesPorRls() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        createFactorAndReturnId(institutionA, YearMonth.of(2025, 6), new BigDecimal("0.074"), "MCTI");
        createFactorAndReturnId(institutionB, YearMonth.of(2025, 6), new BigDecimal("0.09"), "MCTI");

        ResponseEntity<PageResponse<EmissionFactorDTO>> listFromA = listFactors(institutionA, null);

        assertEquals(HttpStatus.OK, listFromA.getStatusCode());
        assertEquals(1, listFromA.getBody().content().size());
        assertFalse(listFromA.getBody().content().stream()
                .anyMatch(f -> f.value().compareTo(new BigDecimal("0.09")) == 0));
    }
}
