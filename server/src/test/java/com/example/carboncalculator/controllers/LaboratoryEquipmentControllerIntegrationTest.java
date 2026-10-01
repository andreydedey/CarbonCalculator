package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryCompositionDTO;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.dto.LoginRequest;

/**
 * Integration tests for LaboratoryEquipment (US-015..US-017) against a real
 * PostgreSQL via Testcontainers with a non-superuser role to validate RLS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LaboratoryEquipmentControllerIntegrationTest {

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

    private HttpHeaders headersFor(UUID institutionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private HttpHeaders authOnlyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private UUID createInstitution(String prefix) {
        String acronym = prefix.substring(0, Math.min(prefix.length(), 4)) + (System.nanoTime() % 1000000);
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + prefix, acronym, "Cidade", "PA");
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authOnlyHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    private UUID createLaboratory(UUID institutionId, String name) {
        ResponseEntity<LaboratoryDTO> response = restTemplate.exchange(
                "/laboratories", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryRequest(name, null), headersFor(institutionId)),
                LaboratoryDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private UUID createEquipmentModel(UUID institutionId, String name) {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                name, "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);
        ResponseEntity<String> response = restTemplate.exchange(
                "/equipment-models", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    private UUID createConfiguration(UUID institutionId, UUID modelId, String os) {
        CreateConfigurationRequest request = new CreateConfigurationRequest(modelId, os, null);
        ResponseEntity<String> response = restTemplate.exchange(
                "/configurations", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
    }

    private ResponseEntity<LaboratoryEquipmentDTO> linkConfiguration(
            UUID institutionId, UUID labId, UUID configId, int quantity) {
        CreateLaboratoryEquipmentRequest request = new CreateLaboratoryEquipmentRequest(configId, quantity);
        return restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)), LaboratoryEquipmentDTO.class);
    }

    // @spec:AC-043
    @Test
    void deveVincularConfiguracaoAoLaboratorioComSistemaOperacionalEQuantidade() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "Dell OptiPlex");
        UUID configId = createConfiguration(institutionId, modelId, "Linux");

        ResponseEntity<LaboratoryEquipmentDTO> response = linkConfiguration(institutionId, labId, configId, 30);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LaboratoryEquipmentDTO body = response.getBody();
        assertNotNull(body.id());
        assertEquals(30, body.quantity());
        assertEquals("Linux", body.operatingSystem());

        ResponseEntity<LaboratoryCompositionDTO> composition = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);
        assertEquals(1, composition.getBody().items().size());
        assertEquals(30, composition.getBody().totalMachines());
    }

    // @spec:AC-044
    @Test
    void deveRecusarVinculacaoComQuantidadeZeroOuNegativa() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "HP EliteDesk");
        UUID configId = createConfiguration(institutionId, modelId, "Windows 11");

        CreateLaboratoryEquipmentRequest zeroRequest = new CreateLaboratoryEquipmentRequest(configId, 0);
        ResponseEntity<Object> zeroResponse = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.POST,
                new HttpEntity<>(zeroRequest, headersFor(institutionId)), Object.class);
        assertEquals(HttpStatus.BAD_REQUEST, zeroResponse.getStatusCode());

        CreateLaboratoryEquipmentRequest negativeRequest = new CreateLaboratoryEquipmentRequest(configId, -5);
        ResponseEntity<Object> negativeResponse = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.POST,
                new HttpEntity<>(negativeRequest, headersFor(institutionId)), Object.class);
        assertEquals(HttpStatus.BAD_REQUEST, negativeResponse.getStatusCode());
    }

    // @spec:AC-045
    @Test
    void deveRecusarVinculacaoDuplicadaDaMesmaConfiguracao() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "Lenovo ThinkCentre");
        UUID configId = createConfiguration(institutionId, modelId, "Windows 10");

        ResponseEntity<LaboratoryEquipmentDTO> first = linkConfiguration(institutionId, labId, configId, 10);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        ResponseEntity<Object> duplicate = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryEquipmentRequest(configId, 5), headersFor(institutionId)),
                Object.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    // @spec:AC-046
    @Test
    void devePemitirMesmoModeloComSistemasOperacionaisDiferentesNoMesmoLab() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "HP 800 G6");
        UUID configWin11 = createConfiguration(institutionId, modelId, "Windows 11");
        UUID configLinux = createConfiguration(institutionId, modelId, "Linux");

        ResponseEntity<LaboratoryEquipmentDTO> win11 = linkConfiguration(institutionId, labId, configWin11, 20);
        ResponseEntity<LaboratoryEquipmentDTO> linux = linkConfiguration(institutionId, labId, configLinux, 10);

        assertEquals(HttpStatus.CREATED, win11.getStatusCode());
        assertEquals(HttpStatus.CREATED, linux.getStatusCode());

        ResponseEntity<LaboratoryCompositionDTO> composition = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);
        assertEquals(2, composition.getBody().items().size());
        assertEquals(30, composition.getBody().totalMachines());
    }

    // @spec:AC-047
    @Test
    void deveRetornarComposicaoComTotaisDeEstacoes() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID model1 = createEquipmentModel(institutionId, "Dell XPS");
        UUID model2 = createEquipmentModel(institutionId, "Apple Mac Mini");
        UUID config1 = createConfiguration(institutionId, model1, "Windows 11");
        UUID config2 = createConfiguration(institutionId, model2, "macOS");

        linkConfiguration(institutionId, labId, config1, 15);
        linkConfiguration(institutionId, labId, config2, 5);

        ResponseEntity<LaboratoryCompositionDTO> response = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LaboratoryCompositionDTO composition = response.getBody();
        assertEquals(2, composition.items().size());
        assertEquals(20, composition.totalMachines());
    }

    // @spec:AC-048
    @Test
    void deveContarConfiguracoeSemMonitorNaComposicao() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "Dell Desktop");
        // Configuration without monitor (monitorId = null) and hasIntegratedScreen = false
        UUID configWithoutMonitor = createConfiguration(institutionId, modelId, "Linux");

        linkConfiguration(institutionId, labId, configWithoutMonitor, 10);

        ResponseEntity<LaboratoryCompositionDTO> response = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().configurationsWithoutMonitor());
    }

    // @spec:AC-049
    @Test
    void deveAtualizarQuantidadeDaConfiguracao() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "Lenovo Yoga");
        UUID configId = createConfiguration(institutionId, modelId, "Windows 11");
        LaboratoryEquipmentDTO linked = linkConfiguration(institutionId, labId, configId, 10).getBody();

        CreateLaboratoryEquipmentRequest update = new CreateLaboratoryEquipmentRequest(configId, 25);
        ResponseEntity<LaboratoryEquipmentDTO> response = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment/" + linked.id(), HttpMethod.PUT,
                new HttpEntity<>(update, headersFor(institutionId)), LaboratoryEquipmentDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(25, response.getBody().quantity());

        ResponseEntity<LaboratoryCompositionDTO> composition = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);
        assertEquals(25, composition.getBody().totalMachines());
    }

    // @spec:AC-050
    @Test
    void deveDesvincularConfiguracaoDoLaboratorioSemExcluirOModelo() {
        UUID institutionId = createInstitution("UFPA");
        UUID labId = createLaboratory(institutionId, "LABCOMP-01");
        UUID modelId = createEquipmentModel(institutionId, "Dell Precision");
        UUID configId = createConfiguration(institutionId, modelId, "Linux");
        LaboratoryEquipmentDTO linked = linkConfiguration(institutionId, labId, configId, 8).getBody();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment/" + linked.id(), HttpMethod.DELETE,
                new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        ResponseEntity<LaboratoryCompositionDTO> composition = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryCompositionDTO.class);
        assertTrue(composition.getBody().items().isEmpty());
        assertEquals(0, composition.getBody().totalMachines());

        // Equipment model still exists in institution
        ResponseEntity<String> models = restTemplate.exchange(
                "/equipment-models?page=0&size=50", HttpMethod.GET,
                new HttpEntity<>(headersFor(institutionId)), String.class);
        assertTrue(models.getBody().contains("Dell Precision"));
    }
}
