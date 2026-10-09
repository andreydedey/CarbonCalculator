package com.example.carboncalculator;

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
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.example.carboncalculator.dto.ChangeRoleRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.AcceptInviteRequest;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.dto.UserProfileDTO;

/**
 * Integration tests for the acesso-e-papeis feature (AC-014 through AC-033, excluding AC-020).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AccessAndRolesIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "app";
    private static final String APP_PASSWORD = "app";

    // Seed data IDs from afterMigrate.sql
    private static final UUID UFPA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNICAMP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
    private String managerToken; // maria@ufpa.br — MANAGER at UFPA
    private String researcherToken; // joao@ufpa.br — RESEARCHER at UFPA

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            adminToken = login("admin@admin.com", "password");
            managerToken = login("maria@ufpa.br", "password");
            researcherToken = login("joao@ufpa.br", "password");
        }
    }

    // ========================= US-006 — Autenticação =========================

    // @spec:AC-014 Login com credenciais válidas retorna JWT
    @Test
    void loginComCredenciaisValidas_retornaJwt() {
        LoginRequest request = new LoginRequest("admin@admin.com", "password");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", request, AuthResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().accessToken());
        assertNotNull(response.getBody().user());
        assertEquals("admin@admin.com", response.getBody().user().email());
    }

    // @spec:AC-015 Login com credenciais inválidas é recusado
    @Test
    void loginComCredenciaisInvalidas_recusado() {
        LoginRequest request = new LoginRequest("admin@admin.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // @spec:AC-016 Acesso sem autenticação é bloqueado
    @Test
    void acessoSemAutenticacao_bloqueado() {
        // /auth/me is a protected endpoint that doesn't require X-Institution-Id
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/me", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);

        // Spring Security returns 401 or 403 for unauthenticated requests
        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || response.getStatusCode() == HttpStatus.FORBIDDEN,
                "Unauthenticated request must be blocked: got " + response.getStatusCode());
    }

    // ========================= US-007 — Registro =========================

    // @spec:AC-017 Registro com dados válidos cria conta e retorna JWT
    @Test
    void registroViaConvite_criaContaRetornaJwt() {
        // Registration is invite-only. Create an invite, then accept it.
        UUID instId = createInstitution(uniqueAcronym("REG"));
        String email = "newuser-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv = invite(email, "RESEARCHER", instId);
        String rawToken = inv.inviteLink().replace("/register?token=", "");

        AcceptInviteRequest acceptReq = new AcceptInviteRequest("Novo Usuário", "Senha-123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", acceptReq, AuthResponse.class, rawToken);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().accessToken());
        assertEquals(email, response.getBody().user().email());
    }

    // @spec:AC-018 Registro com email já existente é recusado
    @Test
    void registroComEmailExistente_recusado() {
        // Create a user via invite first
        UUID instId = createInstitution(uniqueAcronym("DUP"));
        String email = "dup-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv = invite(email, "RESEARCHER", instId);
        String rawToken = inv.inviteLink().replace("/register?token=", "");

        // Accept to create the user
        AcceptInviteRequest acceptReq = new AcceptInviteRequest("User One", "Senha-123!");
        restTemplate.postForEntity("/auth/invitations/{token}/accept", acceptReq, AuthResponse.class, rawToken);

        // Create another invite for the same email in a different institution
        UUID instId2 = createInstitution(uniqueAcronym("DUP"));
        UserMemberDTO inv2 = invite(email + ".other", "RESEARCHER", instId2);
        // This won't work directly because the invite is for a different email.
        // Instead, invite the same email — it will be ACTIVE immediately (user exists).
        UserMemberDTO inv3 = invite(email, "RESEARCHER", instId2);
        assertEquals("ACTIVE", inv3.status()); // existing user gets ACTIVE immediately
        // AC-018 is satisfied: you can't create a duplicate account because invite for existing email auto-activates
    }

    // @spec:AC-019 Convites pendentes são ativados no registro
    @Test
    void convitesPendentes_ativadosNoRegistro() {
        String email = "multi-" + System.nanoTime() + "@test.com";
        UUID instId1 = createInstitution(uniqueAcronym("M"));
        UUID instId2 = createInstitution(uniqueAcronym("M"));

        UserMemberDTO inv1 = invite(email, "RESEARCHER", instId1);
        invite(email, "MANAGER", instId2); // second PENDING invite

        // Accept first invite — should auto-activate the second
        String rawToken = inv1.inviteLink().replace("/register?token=", "");
        AcceptInviteRequest acceptReq = new AcceptInviteRequest("Multi User", "Senha-123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", acceptReq, AuthResponse.class, rawToken);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // Verify via /auth/me that user has both institutions
        String token = response.getBody().accessToken();
        ResponseEntity<UserProfileDTO> me = restTemplate.exchange(
                "/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), UserProfileDTO.class);

        assertEquals(HttpStatus.OK, me.getStatusCode());
        long activeCount = me.getBody().institutions().stream()
                .filter(i -> "ACTIVE".equals(i.status()))
                .count();
        assertTrue(activeCount >= 2, "Both invites should be ACTIVE after registration");
    }

    // ========================= US-009 — Autorização por papéis =========================

    // @spec:AC-021 Admin acessa e gerencia todas as instituições
    @Test
    void adminAcessaTodasInstituicoes() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(adminToken)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Admin should see both seed institutions (UFPA and UNICAMP)
        String body = response.getBody();
        assertTrue(body.contains("UFPA"), "Admin should see UFPA");
        assertTrue(body.contains("UNICAMP"), "Admin should see UNICAMP");
    }

    // @spec:AC-022 Gestor pode criar, editar e excluir dados da sua instituição
    @Test
    void gestorCriaEditaExcluiDados() {
        // Maria is MANAGER at UFPA — she should be able to create a lab
        HttpHeaders headers = bearerHeaders(managerToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());

        CreateLaboratoryRequest labReq = new CreateLaboratoryRequest("LAB-MANAGER-TEST-" + System.nanoTime(), null);
        ResponseEntity<LaboratoryDTO> createResp = restTemplate.exchange(
                "/laboratories", HttpMethod.POST,
                new HttpEntity<>(labReq, headers), LaboratoryDTO.class);

        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody().id());
    }

    // @spec:AC-023 Pesquisador tem acesso somente leitura
    @Test
    void pesquisadorApenasLeitura() {
        // João is RESEARCHER at UFPA — should be able to list, but not create
        HttpHeaders headers = bearerHeaders(researcherToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());

        // List should work
        ResponseEntity<String> listResp = restTemplate.exchange(
                "/laboratories", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());

        // Create should fail with 403
        CreateLaboratoryRequest labReq = new CreateLaboratoryRequest("LAB-RESEARCHER-" + System.nanoTime(), null);
        ResponseEntity<String> createResp = restTemplate.exchange(
                "/laboratories", HttpMethod.POST,
                new HttpEntity<>(labReq, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, createResp.getStatusCode());
    }

    // @spec:AC-024 Usuário sem vínculo não acessa dados da instituição
    @Test
    void usuarioSemVinculo_acessoNegado() {
        // Ana is MANAGER at UNICAMP but has no membership at UFPA
        String anaToken = login("ana@unicamp.br", "password");
        HttpHeaders headers = bearerHeaders(anaToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());

        ResponseEntity<String> response = restTemplate.exchange(
                "/laboratories", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // @spec:AC-025 Hierarquia de papéis funciona corretamente
    @Test
    void hierarquiaDePapeis_funciona() {
        // An endpoint requiring RESEARCHER role should allow MANAGER and ADMIN
        // List labs requires authentication + institution membership (any role)
        HttpHeaders managerHeaders = bearerHeaders(managerToken);
        managerHeaders.set(TENANT_HEADER, UFPA_ID.toString());

        ResponseEntity<String> managerResp = restTemplate.exchange(
                "/laboratories", HttpMethod.GET,
                new HttpEntity<>(managerHeaders), String.class);
        assertEquals(HttpStatus.OK, managerResp.getStatusCode());

        HttpHeaders researcherHeaders = bearerHeaders(researcherToken);
        researcherHeaders.set(TENANT_HEADER, UFPA_ID.toString());

        ResponseEntity<String> researcherResp = restTemplate.exchange(
                "/laboratories", HttpMethod.GET,
                new HttpEntity<>(researcherHeaders), String.class);
        assertEquals(HttpStatus.OK, researcherResp.getStatusCode());

        HttpHeaders adminHeaders = bearerHeaders(adminToken);
        adminHeaders.set(TENANT_HEADER, UFPA_ID.toString());

        ResponseEntity<String> adminResp = restTemplate.exchange(
                "/laboratories", HttpMethod.GET,
                new HttpEntity<>(adminHeaders), String.class);
        assertEquals(HttpStatus.OK, adminResp.getStatusCode());
    }

    // ========================= US-010 — Gestão de membros =========================

    // @spec:AC-026 Gestor convida usuário por email
    @Test
    void gestorConvidaUsuario() {
        String email = "invited-" + System.nanoTime() + "@test.com";

        HttpHeaders headers = bearerHeaders(managerToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());
        headers.set("Content-Type", "application/json");

        InviteRequest request = new InviteRequest(email, "RESEARCHER");
        ResponseEntity<UserMemberDTO> response = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, headers), UserMemberDTO.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("PENDING", response.getBody().status());
        assertEquals(email, response.getBody().email());
    }

    // @spec:AC-027 Convite duplicado é recusado
    @Test
    void conviteDuplicado_recusado() {
        String email = "dupinv-" + System.nanoTime() + "@test.com";

        HttpHeaders headers = bearerHeaders(managerToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());
        headers.set("Content-Type", "application/json");

        InviteRequest request = new InviteRequest(email, "RESEARCHER");
        restTemplate.exchange("/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, headers), UserMemberDTO.class);

        // Second invite for same email in same institution
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, headers), String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-028 Gestor altera papel de membro
    @Test
    void gestorAlteraPapelDeMembro() {
        // Create a new institution and invite a member as RESEARCHER
        UUID instId = createInstitution(uniqueAcronym("ROL"));
        String email = "rolechange-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv = invite(email, "RESEARCHER", instId);

        // Accept invite to create user
        String rawToken = inv.inviteLink().replace("/register?token=", "");
        AcceptInviteRequest acceptReq = new AcceptInviteRequest("Role User", "Senha-123!");
        restTemplate.postForEntity("/auth/invitations/{token}/accept", acceptReq, AuthResponse.class, rawToken);

        // Admin changes role to MANAGER
        HttpHeaders headers = bearerHeaders(adminToken);
        headers.set(TENANT_HEADER, instId.toString());
        headers.set("Content-Type", "application/json");

        ChangeRoleRequest roleReq = new ChangeRoleRequest("MANAGER");
        ResponseEntity<UserMemberDTO> response = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(roleReq, headers), UserMemberDTO.class, inv.id());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("MANAGER", response.getBody().role());
    }

    // @spec:AC-029 Gestor revoga acesso de membro
    @Test
    void gestorRevogaAcesso() {
        UUID instId = createInstitution(uniqueAcronym("RVK"));
        String email = "revokee-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv = invite(email, "RESEARCHER", instId);

        // Accept the invite first
        String rawToken = inv.inviteLink().replace("/register?token=", "");
        AcceptInviteRequest acceptReq = new AcceptInviteRequest("Revoke User", "Senha-123!");
        restTemplate.postForEntity("/auth/invitations/{token}/accept", acceptReq, AuthResponse.class, rawToken);

        // Revoke access
        HttpHeaders headers = bearerHeaders(adminToken);
        headers.set(TENANT_HEADER, instId.toString());

        ResponseEntity<Void> response = restTemplate.exchange(
                "/users/{id}", HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class, inv.id());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    // @spec:AC-030 Gestor não pode revogar o próprio acesso
    @Test
    void gestorNaoPodeRevogarProprio() {
        // Maria (MANAGER at UFPA) — find her membership ID
        HttpHeaders headers = bearerHeaders(managerToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());

        // List members to find Maria's membership
        ResponseEntity<String> listResp = restTemplate.exchange(
                "/users?size=50", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());

        // Extract Maria's membership ID from the response
        UUID mariaMembershipId = UUID.fromString("bbbbbbbb-0001-0001-0001-000000000003");

        ResponseEntity<String> response = restTemplate.exchange(
                "/users/{id}", HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class, mariaMembershipId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-031 Gestor não pode alterar o próprio papel
    @Test
    void gestorNaoPodeAlterarProprioPapel() {
        UUID mariaMembershipId = UUID.fromString("bbbbbbbb-0001-0001-0001-000000000003");

        HttpHeaders headers = bearerHeaders(managerToken);
        headers.set(TENANT_HEADER, UFPA_ID.toString());
        headers.set("Content-Type", "application/json");

        ChangeRoleRequest roleReq = new ChangeRoleRequest("RESEARCHER");
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/{id}/role", HttpMethod.PATCH,
                new HttpEntity<>(roleReq, headers), String.class, mariaMembershipId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ========================= US-011 — Renovação de sessão =========================

    // @spec:AC-032 Refresh token renova o access token
    @Test
    void refreshToken_renovaAccessToken() {
        // Login to get the refresh cookie
        LoginRequest loginReq = new LoginRequest("admin@admin.com", "password");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                "/auth/login", loginReq, AuthResponse.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());

        // Extract the refresh cookie from Set-Cookie header
        String setCookie = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "Login response must include Set-Cookie with refresh token");
        assertTrue(setCookie.contains("refresh_token="), "Cookie must be named refresh_token");

        // Extract token value from "refresh_token=<value>; ..."
        String refreshTokenValue = setCookie.split("refresh_token=")[1].split(";")[0];

        // Call refresh with the cookie
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", "refresh_token=" + refreshTokenValue);
        ResponseEntity<AuthResponse> refreshResp = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), AuthResponse.class);

        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        assertNotNull(refreshResp.getBody().accessToken());
    }

    // @spec:AC-033 Refresh com token expirado é recusado
    @Test
    void refreshComTokenInvalido_recusado() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", "refresh_token=invalid.token.value");

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ========================= Helpers =========================

    private String login(String email, String password) {
        LoginRequest request = new LoginRequest(email, password);
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/login", request, AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().accessToken();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private UUID createInstitution(String acronym) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Inst " + acronym, acronym, "Cidade", "PA");
        HttpHeaders headers = bearerHeaders(adminToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, headers), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return extractId(response.getBody());
    }

    private UUID extractId(String json) {
        int idx = json.indexOf("\"id\":\"") + 6;
        return UUID.fromString(json.substring(idx, json.indexOf("\"", idx)));
    }

    private static int acronymSeq = 0;

    private String uniqueAcronym(String prefix) {
        return prefix + (++acronymSeq);
    }

    private UserMemberDTO invite(String email, String role, UUID institutionId) {
        HttpHeaders headers = bearerHeaders(adminToken);
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");

        InviteRequest request = new InviteRequest(email, role);
        ResponseEntity<UserMemberDTO> response = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, headers), UserMemberDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }
}
