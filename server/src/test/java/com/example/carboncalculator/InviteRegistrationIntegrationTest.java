package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.AcceptInviteRequest;
import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.InviteValidationResponse;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.UserMemberDTO;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InviteRegistrationIntegrationTest {

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

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;
    private String adminToken;
    private UUID institutionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);

        if (adminToken == null) {
            LoginRequest login = new LoginRequest("admin@admin.com", "password");
            ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity(
                    "/auth/login", login, AuthResponse.class);
            assertEquals(HttpStatus.OK, authResp.getStatusCode());
            adminToken = authResp.getBody().accessToken();
        }

        InstitutionDTO institution = createInstitution("INV-" + System.nanoTime());
        institutionId = institution.id();
    }

    // --- @spec:AC-155 invite response includes inviteLink ---

    @Test
    void invitePendingUser_returnsInviteLink() {
        String email = "new-" + System.nanoTime() + "@test.com";
        UserMemberDTO result = invite(email, "RESEARCHER");

        assertEquals("PENDING", result.status());
        assertNotNull(result.inviteLink());
        assertTrue(result.inviteLink().startsWith("/register?token="));
    }

    // --- @spec:AC-156 token stored as SHA-256 hash ---

    @Test
    void inviteToken_storedAsHash() {
        String email = "hash-" + System.nanoTime() + "@test.com";
        UserMemberDTO result = invite(email, "RESEARCHER");

        String tokenHash = jdbcTemplate.queryForObject(
                "SELECT invite_token_hash FROM user_institution WHERE id = ?::uuid",
                String.class, result.id().toString());

        assertNotNull(tokenHash);
        // SHA-256 hex is 64 chars
        assertEquals(64, tokenHash.length());
        // The raw token from the link should NOT equal the stored hash
        String rawToken = result.inviteLink().replace("/register?token=", "");
        assertTrue(!rawToken.equals(tokenHash), "Raw token must not equal stored hash");
    }

    // --- @spec:AC-157 token expires after 7 days ---

    @Test
    void expiredToken_rejected() {
        String email = "expired-" + System.nanoTime() + "@test.com";
        UserMemberDTO result = invite(email, "RESEARCHER");
        String rawToken = result.inviteLink().replace("/register?token=", "");

        // Manually expire the token
        jdbcTemplate.update(
                "UPDATE user_institution SET invite_expires_at = NOW() - INTERVAL '1 day' WHERE id = ?::uuid",
                result.id().toString());

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/auth/invitations/{token}/validate", String.class, rawToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- @spec:AC-158 invite existing user has no link ---

    @Test
    void inviteExistingUser_noInviteLink() {
        // admin@admin.com already exists
        UserMemberDTO result = invite("admin@admin.com", "RESEARCHER");

        assertEquals("ACTIVE", result.status());
        assertNull(result.inviteLink());
    }

    // --- @spec:AC-159 validate returns invite data ---

    @Test
    void validateToken_returnsInviteData() {
        String email = "valid-" + System.nanoTime() + "@test.com";
        UserMemberDTO invited = invite(email, "RESEARCHER");
        String rawToken = invited.inviteLink().replace("/register?token=", "");

        ResponseEntity<InviteValidationResponse> response = restTemplate.getForEntity(
                "/auth/invitations/{token}/validate", InviteValidationResponse.class, rawToken);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(email, response.getBody().email());
        assertEquals("RESEARCHER", response.getBody().role());
        assertNotNull(response.getBody().institutionName());
    }

    // --- @spec:AC-160 invalid token rejected ---

    @Test
    void validateInvalidToken_rejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/auth/invitations/{token}/validate", String.class, "invalid-token-abc123");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- @spec:AC-161 accept invite creates user and activates membership ---

    @Test
    void acceptInvite_createsUserAndActivatesMembership() {
        String email = "accept-" + System.nanoTime() + "@test.com";
        UserMemberDTO invited = invite(email, "RESEARCHER");
        String rawToken = invited.inviteLink().replace("/register?token=", "");

        AcceptInviteRequest request = new AcceptInviteRequest("João Silva", "Senha-123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", request, AuthResponse.class, rawToken);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().accessToken());
        assertEquals(email, response.getBody().user().email());
        assertEquals("João Silva", response.getBody().user().name());

        // Membership should be ACTIVE now
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM user_institution WHERE id = ?::uuid",
                String.class, invited.id().toString());
        assertEquals("ACTIVE", status);
    }

    // --- @spec:AC-162 consumed token cannot be reused ---

    @Test
    void consumedToken_cannotBeReused() {
        String email = "reuse-" + System.nanoTime() + "@test.com";
        UserMemberDTO invited = invite(email, "RESEARCHER");
        String rawToken = invited.inviteLink().replace("/register?token=", "");

        // First use — should succeed
        AcceptInviteRequest request = new AcceptInviteRequest("Maria", "Senha-123!");
        ResponseEntity<AuthResponse> first = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", request, AuthResponse.class, rawToken);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        // Second use — should fail
        ResponseEntity<String> second = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", request, String.class, rawToken);
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode());
    }

    // --- @spec:AC-163 accept activates other PENDING invites for same email ---

    @Test
    void acceptInvite_activatesOtherPendingInvites() {
        String email = "multi-" + System.nanoTime() + "@test.com";

        // Create a second institution
        InstitutionDTO inst2 = createInstitution("INV2-" + System.nanoTime());

        // Invite the same email in both institutions
        UserMemberDTO inv1 = invite(email, "RESEARCHER");
        inviteInInstitution(email, "MANAGER", inst2.id());

        // Accept the first invite — should create user and activate BOTH memberships
        String rawToken = inv1.inviteLink().replace("/register?token=", "");
        AcceptInviteRequest request = new AcceptInviteRequest("Multi User", "Senha-123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", request, AuthResponse.class, rawToken);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Both memberships should be ACTIVE
        int activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_institution WHERE user_email IS NULL AND status = 'ACTIVE' " +
                        "AND user_id = (SELECT id FROM app_user WHERE email = ?)",
                Integer.class, email);
        assertEquals(2, activeCount);
    }

    // --- @spec:AC-164 reject if email already registered ---

    @Test
    void acceptInvite_rejectsIfEmailAlreadyRegistered() {
        // Invite admin email (already registered)
        // First we need to create a pending invite for an email that has a user
        // We'll create a new user via accept, then try another invite for same email
        String email = "dup-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv1 = invite(email, "RESEARCHER");
        String rawToken1 = inv1.inviteLink().replace("/register?token=", "");

        // Accept first invite — creates user
        AcceptInviteRequest request = new AcceptInviteRequest("Dup User", "Senha-123!");
        restTemplate.postForEntity("/auth/invitations/{token}/accept", request, AuthResponse.class, rawToken1);

        // Create another institution, invite same email (will be ACTIVE since user exists)
        InstitutionDTO inst2 = createInstitution("DUP2-" + System.nanoTime());

        // Manually create a PENDING invite with token for the same email
        // (simulating a race condition: invite was created before user registered, but user registered via different invite)
        String fakeEmail = "dup2-" + System.nanoTime() + "@test.com";
        UserMemberDTO inv2 = inviteInInstitution(fakeEmail, "RESEARCHER", inst2.id());
        // Update the email to match the already-registered email
        jdbcTemplate.update(
                "UPDATE user_institution SET user_email = ? WHERE id = ?::uuid",
                email, inv2.id().toString());

        String rawToken2 = inv2.inviteLink().replace("/register?token=", "");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept", request, String.class, rawToken2);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // --- @spec:AC-167 old register endpoint removed ---

    @Test
    void oldRegisterEndpoint_returns401() {
        // POST /auth/register should no longer exist (returns 401 since it's not in permitAll)
        var request = new java.util.HashMap<String, String>();
        request.put("name", "Test");
        request.put("email", "test@test.com");
        request.put("password", "password123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/register", request, String.class);

        // Should be 401 (not authenticated, and not in public endpoints list)
        assertTrue(response.getStatusCode() == HttpStatus.UNAUTHORIZED
                || response.getStatusCode() == HttpStatus.FORBIDDEN
                || response.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 401/403/404 but got " + response.getStatusCode());
    }

    // --- Helpers ---

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders adminHeaders(UUID instId) {
        HttpHeaders headers = adminHeaders();
        headers.set(TENANT_HEADER, instId.toString());
        return headers;
    }

    private InstitutionDTO createInstitution(String acronym) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronym, acronym, "Cidade", "PA");
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private UserMemberDTO invite(String email, String role) {
        return inviteInInstitution(email, role, institutionId);
    }

    private UserMemberDTO inviteInInstitution(String email, String role, UUID instId) {
        InviteRequest request = new InviteRequest(email, role);
        ResponseEntity<UserMemberDTO> response = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders(instId)), UserMemberDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }
}
