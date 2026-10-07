package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.dto.CreateConsumptionMeasurementRequest;
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.CreateMonitorRequest;
import com.example.carboncalculator.dto.CreateOperatingSystemRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.entities.ShiftType;
import com.example.carboncalculator.entities.TargetType;

/**
 * Integration tests for consumption measurements (PRD 04): registration, conditions, multiple
 * measurements, discrepancy alert, tenant isolation, operating systems and how measurements feed
 * the emission calculation and its readiness check.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ConsumptionMeasurementIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "app";
    private static final String APP_PASSWORD = "app";
    private static final LocalDate MEASURED_ON = LocalDate.of(2026, 9, 1);

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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private UUID institutionId;
    private UUID modelId;
    private UUID osId;
    private UUID monitorId;

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity(
                    "/auth/login", new LoginRequest("admin@admin.com", "password"), AuthResponse.class);
            assertEquals(HttpStatus.OK, authResp.getStatusCode());
            adminToken = authResp.getBody().accessToken();
        }
        institutionId = createInstitution("MED-" + System.nanoTime());
        modelId = createEquipmentModel("Desktop-" + System.nanoTime(), 65);
        osId = createOperatingSystem("Windows 10");
        monitorId = createMonitor("Monitor-" + System.nanoTime(), 21);
    }

    // ========================= REGISTRATION =========================

    // @spec:AC-119
    @Test
    void deveVincularMedicaoDoComputadorAoModeloESistema() {
        JsonNode created = createMeasurement(TargetType.COMPUTER, modelId, osId, null, "90", 4, null, HttpStatus.CREATED);

        JsonNode measurement = created.get("measurement");
        assertEquals(modelId.toString(), measurement.get("equipmentModel").get("id").asText());
        assertEquals(osId.toString(), measurement.get("operatingSystem").get("id").asText());

        JsonNode page = get("/consumption-measurements?equipmentModelId=" + modelId);
        assertEquals(1, page.get("totalElements").asInt());
    }

    // @spec:AC-125
    @Test
    void deveGuardarCondicoesEIntervaloDaMedicao() {
        String conditions = "Navegador com 5 abas, monitor via HDMI";
        JsonNode created = createMeasurement(TargetType.MONITOR, null, null, monitorId, "22.5", 4, conditions, HttpStatus.CREATED);

        JsonNode measurement = get("/consumption-measurements/" + created.get("measurement").get("id").asText());
        assertEquals(12, measurement.get("durationMinutes").asInt());
        assertEquals(4, measurement.get("readingIntervalMinutes").asInt());
        assertEquals(conditions, measurement.get("conditions").asText());
    }

    // @spec:AC-133
    @Test
    void deveAlertarMedicaoDiscrepanteSemImpedirORegistro() {
        createMeasurement(TargetType.COMPUTER, modelId, osId, null, "100", 4, null, HttpStatus.CREATED);
        JsonNode second = createMeasurement(TargetType.COMPUTER, modelId, osId, null, "110", 4, null, HttpStatus.CREATED);
        assertTrue(second.get("outlierWarning").isNull());

        JsonNode outlier = createMeasurement(TargetType.COMPUTER, modelId, osId, null, "300", 4, null, HttpStatus.CREATED);

        JsonNode warning = outlier.get("outlierWarning");
        assertFalse(warning.isNull());
        assertTrue(warning.get("deviationPercent").asDouble() > 50);
        assertEquals(3, get("/consumption-measurements?equipmentModelId=" + modelId).get("totalElements").asInt());
    }

    // ========================= CALCULATION =========================

    // @spec:AC-130
    @Test
    void deveInformarAOrigemDoConsumoDeCadaConfiguracao() {
        UUID measuredConfig = createConfiguration(modelId, osId, monitorId);
        UUID otherModel = createEquipmentModel("Notebook-" + System.nanoTime(), 45);
        UUID unmeasuredConfig = createConfiguration(otherModel, osId, monitorId);
        UUID periodId = setupLabWithSchedule(List.of(measuredConfig, unmeasuredConfig));

        createMeasurement(TargetType.COMBINED, modelId, osId, monitorId, "110", 4, null, HttpStatus.CREATED);

        JsonNode sources = get("/academic-periods/" + periodId + "/emissions").get("consumptionSources");
        assertEquals("measurement_combined", sourceOf(sources, measuredConfig).get("consumptionSource").asText());
        assertEquals(110, sourceOf(sources, measuredConfig).get("totalWatts").asInt());
        assertEquals("specification", sourceOf(sources, unmeasuredConfig).get("consumptionSource").asText());
        assertEquals(45 + 21, sourceOf(sources, unmeasuredConfig).get("totalWatts").asInt());
    }

    // @spec:AC-132
    @Test
    void deveGuardarVariasMedicoesEUsarAMaisRecente() {
        UUID configId = createConfiguration(modelId, osId, monitorId);
        UUID periodId = setupLabWithSchedule(List.of(configId));

        createMeasurement(TargetType.COMPUTER, modelId, osId, null, "90", 4, null, HttpStatus.CREATED);
        createMeasurement(TargetType.COMPUTER, modelId, osId, null, "120", 4, null, HttpStatus.CREATED);

        assertEquals(2, get("/consumption-measurements?equipmentModelId=" + modelId).get("totalElements").asInt());
        JsonNode source = sourceOf(get("/academic-periods/" + periodId + "/emissions").get("consumptionSources"), configId);
        assertEquals(120, source.get("computerWatts").asInt());
        assertEquals("measurement_computer+specification_monitor", source.get("consumptionSource").asText());
    }

    // @spec:AC-129
    @Test
    void deveBloquearCalculoQuandoUmaParteNaoTemMedicaoNemEspecificacao() {
        UUID noSpecModel = createEquipmentModel("Sem-TDP-" + System.nanoTime(), null);
        UUID configId = createConfiguration(noSpecModel, osId, monitorId);
        UUID periodId = setupLabWithSchedule(List.of(configId));

        JsonNode readiness = get("/academic-periods/" + periodId + "/emissions/readiness");
        assertFalse(readiness.get("ready").asBoolean());
        JsonNode warning = readiness.get("configurationsWithoutConsumption").get(0);
        assertEquals(configId.toString(), warning.get("configurationId").asText());
        assertEquals("computador", warning.get("missingParts").get(0).asText());

        createMeasurement(TargetType.COMPUTER, noSpecModel, osId, null, "80", 4, null, HttpStatus.CREATED);

        JsonNode after = get("/academic-periods/" + periodId + "/emissions/readiness");
        assertTrue(after.get("ready").asBoolean());
        assertTrue(after.get("configurationsWithoutConsumption").isEmpty());
    }

    // ========================= ISOLATION =========================

    // @spec:AC-134
    @Test
    void naoDeveMostrarMedicoesDeOutraInstituicao() {
        createMeasurement(TargetType.MONITOR, null, null, monitorId, "21", 4, null, HttpStatus.CREATED);

        institutionId = createInstitution("OUT-" + System.nanoTime());

        assertEquals(0, get("/consumption-measurements").get("totalElements").asInt());
    }

    // @spec:AC-135
    @Test
    void deveRecusarSistemaOperacionalRepetidoNaMesmaInstituicao() {
        ResponseEntity<String> duplicate = restTemplate.exchange("/operating-systems", HttpMethod.POST,
                new HttpEntity<>(new CreateOperatingSystemRequest("Windows 10"), headers()), String.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        institutionId = createInstitution("OUT-" + System.nanoTime());
        createOperatingSystem("Windows 10");
    }

    // ========================= HELPERS =========================

    private JsonNode createMeasurement(TargetType type, UUID model, UUID os, UUID monitor, String watts,
                                       Integer interval, String conditions, HttpStatus expected) {
        CreateConsumptionMeasurementRequest request = new CreateConsumptionMeasurementRequest(
                type, model, os, monitor, new BigDecimal(watts), 12, interval, MEASURED_ON, conditions);
        ResponseEntity<String> response = restTemplate.exchange("/consumption-measurements", HttpMethod.POST,
                new HttpEntity<>(request, headers()), String.class);
        assertEquals(expected, response.getStatusCode(), response.getBody());
        return read(response.getBody());
    }

    /** Lab with the given configurations (5 stations each), March 2025, Mon–Fri slots 1–4. */
    private UUID setupLabWithSchedule(List<UUID> configIds) {
        post("/emission-factors", new CreateEmissionFactorRequest(YearMonth.of(2025, 3), new BigDecimal("0.0425"), "SIN"));
        UUID labId = UUID.fromString(post("/laboratories", new CreateLaboratoryRequest("LAB-" + System.nanoTime(), null))
                .get("id").asText());
        for (UUID configId : configIds) {
            post("/laboratories/" + labId + "/equipment", new CreateLaboratoryEquipmentRequest(configId, 5));
        }
        UUID periodId = UUID.fromString(post("/academic-periods", new CreateAcademicPeriodRequest(
                "P-" + System.nanoTime(), LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31))).get("id").asText());

        ResponseEntity<List<ShiftDTO>> shifts = restTemplate.exchange(
                "/academic-periods/" + periodId + "/shifts", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceShiftsRequest(List.of(new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 5, 50, 10, List.of(1, 2, 3, 4, 5), true))), headers()),
                new ParameterizedTypeReference<>() {});
        UUID shiftId = shifts.getBody().get(0).id();

        List<ReplaceScheduleRequest.ScheduleInput> grid = List.of(1, 2, 3, 4, 5).stream()
                .map(day -> new ReplaceScheduleRequest.ScheduleInput(shiftId, day, List.of(1, 2, 3, 4)))
                .toList();
        ResponseEntity<String> schedule = restTemplate.exchange(
                "/academic-periods/" + periodId + "/laboratories/" + labId + "/schedule", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(grid), headers()), String.class);
        assertEquals(HttpStatus.OK, schedule.getStatusCode(), schedule.getBody());
        return periodId;
    }

    private static JsonNode sourceOf(JsonNode sources, UUID configId) {
        for (JsonNode source : sources) {
            if (configId.toString().equals(source.get("configurationId").asText())) return source;
        }
        throw new AssertionError("No consumption source for configuration " + configId + ": " + sources);
    }

    private UUID createInstitution(String acronym) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange("/institutions", HttpMethod.POST,
                new HttpEntity<>(new CreateInstitutionRequest("Instituição " + acronym, acronym, "Cidade", "PA"), headers),
                InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private UUID createEquipmentModel(String name, Integer tdpWatts) {
        return UUID.fromString(post("/equipment-models", new CreateEquipmentModelRequest(
                name, "DESKTOP", "Intel i5", tdpWatts, 4, 8, null, null, false, null)).get("id").asText());
    }

    private UUID createMonitor(String name, int watts) {
        return UUID.fromString(post("/monitors", new CreateMonitorRequest(name, watts)).get("id").asText());
    }

    private UUID createOperatingSystem(String name) {
        return UUID.fromString(post("/operating-systems", new CreateOperatingSystemRequest(name)).get("id").asText());
    }

    private UUID createConfiguration(UUID model, UUID os, UUID monitor) {
        return UUID.fromString(post("/configurations", new CreateConfigurationRequest(model, os, monitor)).get("id").asText());
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private JsonNode post(String url, Object body) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        return read(response.getBody());
    }

    private JsonNode get(String url) {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return read(response.getBody());
    }

    private static JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON: " + body, e);
        }
    }
}
