package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.ClassOccurrenceDTO;
import com.example.carboncalculator.dto.ConfigurationDTO;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.CreateMonitorRequest;
import com.example.carboncalculator.dto.DayClassesDTO;
import com.example.carboncalculator.dto.EmissionResultDTO;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.dto.HolidayDTO;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.MonitorDTO;
import com.example.carboncalculator.dto.ReadinessDTO;
import com.example.carboncalculator.dto.ReplaceHolidaysRequest;
import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ScheduleEntryDTO;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.dto.UpsertClassOccurrenceRequest;
import com.example.carboncalculator.entities.ClassSessionStatus;
import com.example.carboncalculator.entities.HolidayType;
import com.example.carboncalculator.entities.ShiftType;

/**
 * Integration tests for stations used per class, per-date exceptions, realized vs. projected
 * emissions and the day view (ADR-007).
 *
 * <p>Standard scenario: one lab with 10 stations of 86 W (65 W computer + 21 W monitor),
 * March 2025, morning shift with 5 classes of 50 min, grid Mon–Fri slots 1–4 using 6 stations,
 * holiday on 2025-03-04. That gives 20 school days × 4 classes, each consuming
 * 86 W × 6 × 50/60 h = 0.43 kWh.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ClassOccurrenceIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "app";
    private static final String APP_PASSWORD = "app";
    private static final double CLASS_KWH = 86 * 6 * (50 / 60.0) / 1000.0;

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

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String adminToken;
    private UUID institutionId;
    private UUID labId;
    private UUID periodId;
    private UUID shiftId;

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity(
                    "/auth/login", new LoginRequest("admin@admin.com", "password"), AuthResponse.class);
            assertEquals(HttpStatus.OK, authResp.getStatusCode());
            adminToken = authResp.getBody().accessToken();
        }
        institutionId = createInstitution("OCC-" + System.nanoTime()).id();
    }

    // ========================= GRID STATIONS =========================

    @Test
    void deveGuardarEstacoesUsadasPorAulaNaGrade() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        List<ScheduleEntryDTO> schedule = getSchedule();
        ScheduleEntryDTO monday = schedule.stream().filter(e -> e.dayOfWeek() == 1).findFirst().orElseThrow();
        assertEquals(List.of(1, 2, 3, 4), monday.occupiedSlots());
        assertEquals(List.of(6, 6, 6, 6), monday.stationsUsed());
    }

    @Test
    void deveUsarCapacidadeDoLaboratorioQuandoEstacoesNaoSaoInformadas() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        replaceSchedule(List.of(new ReplaceScheduleRequest.ScheduleInput(shiftId, 1, List.of(1, 2))), HttpStatus.OK);

        ScheduleEntryDTO monday = getSchedule().get(0);
        assertEquals(List.of(10, 10), monday.stationsUsed());
    }

    @Test
    void deveRejeitarEstacoesAcimaDaCapacidadeOuSemCorrespondencia() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        replaceSchedule(List.of(new ReplaceScheduleRequest.ScheduleInput(shiftId, 1, List.of(1), List.of(11))),
                HttpStatus.BAD_REQUEST);
        replaceSchedule(List.of(new ReplaceScheduleRequest.ScheduleInput(shiftId, 1, List.of(1, 2), List.of(5))),
                HttpStatus.BAD_REQUEST);
        replaceSchedule(List.of(new ReplaceScheduleRequest.ScheduleInput(shiftId, 1, List.of(1), List.of(0))),
                HttpStatus.BAD_REQUEST);
    }

    // ========================= EXCEPTIONS =========================

    @Test
    void deveRegistrarCancelamentoAjusteEAulaExtra() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        ClassOccurrenceDTO cancelled = upsert(LocalDate.of(2025, 3, 12), 1, 0, HttpStatus.OK);
        assertEquals(ClassSessionStatus.CANCELLED, cancelled.status());
        assertEquals(6, cancelled.gridStations());

        ClassOccurrenceDTO adjusted = upsert(LocalDate.of(2025, 3, 13), 2, 3, HttpStatus.OK);
        assertEquals(ClassSessionStatus.ADJUSTED, adjusted.status());

        ClassOccurrenceDTO extra = upsert(LocalDate.of(2025, 3, 14), 5, 10, HttpStatus.OK);
        assertEquals(ClassSessionStatus.EXTRA, extra.status());
        assertNull(extra.gridStations());

        assertEquals(3, listOccurrences().size());
    }

    @Test
    void deveRemoverExcecaoQuandoValorVoltaAoPadraoDaGrade() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        upsert(LocalDate.of(2025, 3, 13), 2, 3, HttpStatus.OK);
        upsert(LocalDate.of(2025, 3, 13), 2, 6, HttpStatus.NO_CONTENT);
        assertTrue(listOccurrences().isEmpty());

        upsert(LocalDate.of(2025, 3, 12), 1, 0, HttpStatus.OK);
        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/academic-periods/" + periodId + "/laboratories/" + labId + "/occurrences"
                        + "?shiftId=" + shiftId + "&date=2025-03-12&slot=1",
                HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
        assertTrue(listOccurrences().isEmpty());
    }

    @Test
    void deveRejeitarExcecaoEmFeriadoForaDoPeriodoOuAcimaDaCapacidade() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        upsert(LocalDate.of(2025, 3, 4), 1, 0, HttpStatus.BAD_REQUEST);   // holiday
        upsert(LocalDate.of(2025, 4, 1), 1, 0, HttpStatus.BAD_REQUEST);   // outside the period
        upsert(LocalDate.of(2025, 3, 8), 1, 5, HttpStatus.BAD_REQUEST);   // Saturday, inactive day
        upsert(LocalDate.of(2025, 3, 12), 9, 5, HttpStatus.BAD_REQUEST);  // slot out of range
        upsert(LocalDate.of(2025, 3, 12), 1, 11, HttpStatus.BAD_REQUEST); // above capacity
    }

    // ========================= CALCULATION =========================

    @Test
    void deveCalcularComEstacoesUsadasEExcecoes() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        EmissionResultDTO base = getEmissions();
        assertEquals(80 * CLASS_KWH, base.totalEnergyKwh(), 0.02);

        upsert(LocalDate.of(2025, 3, 12), 1, 0, HttpStatus.OK);    // -1 class
        upsert(LocalDate.of(2025, 3, 13), 2, 3, HttpStatus.OK);    // half a class
        upsert(LocalDate.of(2025, 3, 14), 5, 10, HttpStatus.OK);   // extra with all 10 stations

        double extraKwh = 86 * 10 * (50 / 60.0) / 1000.0;
        double expected = 80 * CLASS_KWH - CLASS_KWH - CLASS_KWH / 2 + extraKwh;

        EmissionResultDTO result = getEmissions();
        assertEquals(expected, result.totalEnergyKwh(), 0.02);
        assertEquals(expected * 0.0425, result.totalEmissionKg(), 0.02);

        EmissionResultDTO.LaboratoryEmission lab = result.byLaboratory().stream()
                .filter(l -> l.laboratoryId().equals(labId)).findFirst().orElseThrow();
        assertEquals(1, lab.cancelledClasses());
        assertEquals(1, lab.adjustedClasses());
        assertEquals(1, lab.extraClasses());
    }

    @Test
    void devePeriodoEncerradoTerRealizadoIgualAProjecao() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        EmissionResultDTO result = getEmissions();
        assertEquals("2025-03-31", result.realizedUntil());
        assertEquals(result.totalEmissionKg(), result.projectedEmissionKg(), 0.001);
        assertEquals(20, result.schoolDaysTotal());
        assertEquals(20, result.schoolDaysElapsed());
    }

    @Test
    void devePeriodoEmAndamentoSepararRealizadoDaProjecao() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        setupStandardScenario(today.minusDays(14), today.plusDays(14));

        EmissionResultDTO result = getEmissions();
        assertEquals(today.minusDays(1).toString(), result.realizedUntil());
        assertTrue(result.totalEmissionKg() > 0);
        assertTrue(result.projectedEmissionKg() > result.totalEmissionKg());
        assertTrue(result.schoolDaysElapsed() < result.schoolDaysTotal());
    }

    @Test
    void deveCalcularMesmoComLaboratorioSemGrade() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        UUID labWithoutGrid = createLab("LAB-SEM-GRADE-" + System.nanoTime());
        UUID modelId = createEquipmentModel("Desktop-" + System.nanoTime(), 65);
        assignEquipment(labWithoutGrid, createConfiguration(modelId, "Linux", null), 5);

        ResponseEntity<ReadinessDTO> readiness = restTemplate.exchange(
                "/academic-periods/" + periodId + "/emissions/readiness", HttpMethod.GET,
                new HttpEntity<>(headers()), ReadinessDTO.class);
        assertTrue(readiness.getBody().ready());
        assertTrue(readiness.getBody().laboratoriesWithoutSchedule().stream()
                .anyMatch(name -> name.startsWith("LAB-SEM-GRADE")));

        EmissionResultDTO result = getEmissions();
        assertEquals(80 * CLASS_KWH, result.totalEnergyKwh(), 0.02);
    }

    // ========================= DAY VIEW =========================

    @Test
    void deveListarAulasDoDiaComStatus() {
        setupStandardScenario(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        upsert(LocalDate.of(2025, 3, 12), 1, 0, HttpStatus.OK);
        upsert(LocalDate.of(2025, 3, 12), 2, 4, HttpStatus.OK);
        upsert(LocalDate.of(2025, 3, 12), 5, 8, HttpStatus.OK);

        DayClassesDTO day = getDay("2025-03-12");
        assertEquals(periodId, day.periodId());
        assertTrue(day.schoolDay());
        List<DayClassesDTO.DayClass> classes = day.classes();
        assertEquals(5, classes.size());
        assertEquals(ClassSessionStatus.CANCELLED, classes.get(0).status());
        assertEquals(ClassSessionStatus.ADJUSTED, classes.get(1).status());
        assertEquals(ClassSessionStatus.GRID, classes.get(2).status());
        assertEquals(ClassSessionStatus.EXTRA, classes.get(4).status());
        assertEquals("07:30", classes.get(0).startTime());
        assertEquals(10, classes.get(0).capacity());

        DayClassesDTO holiday = getDay("2025-03-04");
        assertTrue(!holiday.schoolDay());
        assertEquals("Carnaval", holiday.holidayName());
        assertTrue(holiday.classes().isEmpty());
    }

    // ========================= HELPERS =========================

    private void setupStandardScenario(LocalDate start, LocalDate end) {
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            createEmissionFactor(m, "0.0425");
        }
        labId = createLab("LAB-OCC-" + System.nanoTime());
        UUID modelId = createEquipmentModel("Desktop-" + System.nanoTime(), 65);
        UUID monitorId = createMonitor("Monitor-" + System.nanoTime(), 21);
        assignEquipment(labId, createConfiguration(modelId, "Linux", monitorId), 10);

        periodId = createPeriod("P-" + System.nanoTime(), start, end);
        List<ShiftDTO> shifts = replaceShifts(List.of(new ReplaceShiftsRequest.ShiftInput(
                ShiftType.MORNING, LocalTime.of(7, 30), 5, 50, 10, List.of(1, 2, 3, 4, 5), true)));
        shiftId = shifts.get(0).id();

        LocalDate holiday = LocalDate.of(2025, 3, 4);
        if (!holiday.isBefore(start) && !holiday.isAfter(end)) {
            replaceHolidays(List.of(new HolidayDTO(holiday, "Carnaval", HolidayType.RECESS)));
        }

        List<ReplaceScheduleRequest.ScheduleInput> grid = List.of(1, 2, 3, 4, 5).stream()
                .map(day -> new ReplaceScheduleRequest.ScheduleInput(shiftId, day, List.of(1, 2, 3, 4), List.of(6, 6, 6, 6)))
                .toList();
        replaceSchedule(grid, HttpStatus.OK);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private InstitutionDTO createInstitution(String acronym) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange("/institutions", HttpMethod.POST,
                new HttpEntity<>(new CreateInstitutionRequest("Instituição " + acronym, acronym, "Cidade", "PA"), headers),
                InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private UUID createLab(String name) {
        return post("/laboratories", new CreateLaboratoryRequest(name, null), LaboratoryDTO.class).id();
    }

    private UUID createEquipmentModel(String name, int tdpWatts) {
        return post("/equipment-models", new CreateEquipmentModelRequest(
                name, "DESKTOP", "Intel i5", tdpWatts, 4, 8, null, null, false, null), EquipmentModelDTO.class).id();
    }

    private UUID createMonitor(String name, int watts) {
        return post("/monitors", new CreateMonitorRequest(name, watts), MonitorDTO.class).id();
    }

    private UUID createConfiguration(UUID modelId, String os, UUID monitorId) {
        return post("/configurations", new CreateConfigurationRequest(modelId, os, monitorId), ConfigurationDTO.class).id();
    }

    private void assignEquipment(UUID lab, UUID configId, int quantity) {
        post("/laboratories/" + lab + "/equipment", new CreateLaboratoryEquipmentRequest(configId, quantity), String.class);
    }

    private UUID createPeriod(String name, LocalDate start, LocalDate end) {
        return post("/academic-periods", new CreateAcademicPeriodRequest(name, start, end), AcademicPeriodDTO.class).id();
    }

    private void createEmissionFactor(YearMonth month, String value) {
        post("/emission-factors", new CreateEmissionFactorRequest(month, new BigDecimal(value), "SIN " + month), String.class);
    }

    private <T> T post(String url, Object body, Class<T> type) {
        ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers()), type);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private List<ShiftDTO> replaceShifts(List<ReplaceShiftsRequest.ShiftInput> shifts) {
        ResponseEntity<List<ShiftDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/shifts", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceShiftsRequest(shifts), headers()), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private void replaceHolidays(List<HolidayDTO> holidays) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/holidays", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(holidays), headers()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private void replaceSchedule(List<ReplaceScheduleRequest.ScheduleInput> entries, HttpStatus expected) {
        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/laboratories/" + labId + "/schedule", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headers()), String.class);
        assertEquals(expected, response.getStatusCode(), response.getBody());
    }

    private List<ScheduleEntryDTO> getSchedule() {
        ResponseEntity<List<ScheduleEntryDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/laboratories/" + labId + "/schedule", HttpMethod.GET,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ClassOccurrenceDTO upsert(LocalDate date, int slot, int stations, HttpStatus expected) {
        String url = "/academic-periods/" + periodId + "/laboratories/" + labId + "/occurrences";
        HttpEntity<UpsertClassOccurrenceRequest> body =
                new HttpEntity<>(new UpsertClassOccurrenceRequest(shiftId, date, slot, stations), headers());
        if (expected != HttpStatus.OK) {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, body, String.class);
            assertEquals(expected, response.getStatusCode(), response.getBody());
            return null;
        }
        ResponseEntity<ClassOccurrenceDTO> response = restTemplate.exchange(url, HttpMethod.PUT, body, ClassOccurrenceDTO.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private List<ClassOccurrenceDTO> listOccurrences() {
        ResponseEntity<List<ClassOccurrenceDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/occurrences?laboratoryId=" + labId, HttpMethod.GET,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private EmissionResultDTO getEmissions() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/emissions", HttpMethod.GET,
                new HttpEntity<>(headers()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return read(response.getBody(), EmissionResultDTO.class);
    }

    private DayClassesDTO getDay(String date) {
        ResponseEntity<DayClassesDTO> response = restTemplate.exchange(
                "/class-sessions?date=" + date, HttpMethod.GET, new HttpEntity<>(headers()), DayClassesDTO.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private static <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + type.getSimpleName() + ": " + body, e);
        }
    }
}
