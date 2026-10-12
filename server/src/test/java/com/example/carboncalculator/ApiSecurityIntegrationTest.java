package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.AcceptInviteRequest;
import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.UserMemberDTO;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiSecurityIntegrationTest {

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
    private UUID institutionId;

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                    "/auth/login", new LoginRequest("admin@admin.com", "password"), AuthResponse.class);
            assertEquals(HttpStatus.OK, login.getStatusCode());
            adminToken = login.getBody().accessToken();
        }
        institutionId = createInstitution("SEC-" + System.nanoTime()).id();
    }

    private HttpHeaders headers(String token, UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (tenantId != null) headers.set(TENANT_HEADER, tenantId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String body, String token, UUID tenantId) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers(token, tenantId)), String.class);
    }

    private InstitutionDTO createInstitution(String acronym) {
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(new CreateInstitutionRequest("Instituição " + acronym, acronym, "Cidade", "PA"),
                        headers(adminToken, null)),
                InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private String researcherTokenFor(UUID instId) {
        ResponseEntity<UserMemberDTO> invite = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(new InviteRequest("researcher-" + System.nanoTime() + "@example.com", "RESEARCHER"),
                        headers(adminToken, instId)),
                UserMemberDTO.class);
        assertEquals(HttpStatus.CREATED, invite.getStatusCode());
        String rawToken = invite.getBody().inviteLink().replace("/register?token=", "");
        ResponseEntity<AuthResponse> accepted = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept",
                new AcceptInviteRequest("Pesquisador", "Senha-123!"), AuthResponse.class, rawToken);
        assertEquals(HttpStatus.CREATED, accepted.getStatusCode());
        return accepted.getBody().accessToken();
    }

    // ========================= Authentication =========================

    @Test
    void deveResponder401SemToken() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/laboratories", HttpMethod.GET, null, null, institutionId).getStatusCode());
    }

    @Test
    void deveResponder401ComTokenInvalidoOuExpiradoParaOFrontRenovarASessao() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                exchange("/laboratories", HttpMethod.GET, null, "abc.def.ghi", institutionId).getStatusCode());
    }

    @Test
    void deveHealthcheckResponder200SemAutenticacaoNemInstituicao() {
        ResponseEntity<String> response = exchange("/actuator/health", HttpMethod.GET, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("UP"));
    }

    @Test
    void deveMensagemDoFiltroDeInstituicaoSerUtf8() {
        ResponseEntity<String> response = exchange("/laboratories", HttpMethod.GET, null, adminToken, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getHeaders().getContentType().getCharset().name().equals("UTF-8"));
        assertTrue(response.getBody().contains("é obrigatório"));
    }

    // ========================= Malformed requests =========================

    @Test
    void deveResponder400ParaJsonMalformado() {
        assertEquals(HttpStatus.BAD_REQUEST,
                exchange("/laboratories", HttpMethod.POST, "{oops", adminToken, institutionId).getStatusCode());
    }

    @Test
    void deveResponder400ParaUuidInvalidoNoPath() {
        assertEquals(HttpStatus.BAD_REQUEST,
                exchange("/laboratories/nao-e-uuid", HttpMethod.GET, null, adminToken, institutionId).getStatusCode());
    }

    @Test
    void deveResponder400ParaDataInvalidaNaQuery() {
        assertEquals(HttpStatus.BAD_REQUEST,
                exchange("/snapshots?startDate=ontem&endDate=x", HttpMethod.GET, null, adminToken, institutionId)
                        .getStatusCode());
    }

    @Test
    void deveResponder404ParaRotaInexistente() {
        assertEquals(HttpStatus.NOT_FOUND,
                exchange("/nao-existe", HttpMethod.GET, null, adminToken, institutionId).getStatusCode());
    }

    @Test
    void deveResponder405ParaMetodoNaoSuportado() {
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED,
                exchange("/snapshots", HttpMethod.DELETE, null, adminToken, institutionId).getStatusCode());
    }

    // ========================= Institution access =========================

    @Test
    void deveMembroLerSuaInstituicaoMasNaoAsDeOutros() {
        String researcherToken = researcherTokenFor(institutionId);
        UUID otherInstitutionId = createInstitution("OTH-" + System.nanoTime()).id();

        assertEquals(HttpStatus.OK,
                exchange("/institutions/" + institutionId, HttpMethod.GET, null, researcherToken, null)
                        .getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                exchange("/institutions/" + otherInstitutionId, HttpMethod.GET, null, researcherToken, null)
                        .getStatusCode());
        assertEquals(HttpStatus.OK,
                exchange("/institutions/" + otherInstitutionId, HttpMethod.GET, null, adminToken, null)
                        .getStatusCode());
    }
}
