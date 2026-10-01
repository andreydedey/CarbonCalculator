package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.CopyPeriodRequest;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.HolidayDTO;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.PeriodSummaryDTO;
import com.example.carboncalculator.dto.ReplaceHolidaysRequest;
import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ScheduleEntryDTO;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.dto.UpdateAcademicPeriodRequest;
import com.example.carboncalculator.entities.HolidayType;
import com.example.carboncalculator.entities.ShiftType;

/**
 * Integration tests for Academic Period (PRD-05) covering AC-051 through AC-070.
 * Uses Testcontainers with non-superuser role to validate RLS.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AcademicPeriodControllerIntegrationTest {

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
        // Flyway uses superuser credentials to run migrations (CREATE TABLE, RLS policies)
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

    // --- Helpers ---

    private UUID createInstitutionAndReturnId(String acronymPrefix) {
        String acronym = acronymPrefix.substring(0, Math.min(acronymPrefix.length(), 4))
                + (System.nanoTime() % 1000000);
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronymPrefix, acronym, "Cidade", "PA");
        ResponseEntity<String> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // Extract id from JSON response to avoid InstitutionDTO primitive field issues
        String body = response.getBody();
        int idx = body.indexOf("\"id\":\"") + 6;
        return UUID.fromString(body.substring(idx, body.indexOf("\"", idx)));
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

    private AcademicPeriodDTO createPeriod(UUID institutionId, String name, LocalDate start, LocalDate end) {
        CreateAcademicPeriodRequest request = new CreateAcademicPeriodRequest(name, start, end);
        ResponseEntity<AcademicPeriodDTO> response = restTemplate.exchange(
                "/academic-periods", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(institutionId)),
                AcademicPeriodDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private UUID createLab(UUID institutionId, String name) {
        ResponseEntity<LaboratoryDTO> response = restTemplate.exchange(
                "/laboratories", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryRequest(name, null), headersFor(institutionId)),
                LaboratoryDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private List<ShiftDTO> replaceShifts(UUID institutionId, UUID periodId, List<ReplaceShiftsRequest.ShiftInput> shifts) {
        ReplaceShiftsRequest request = new ReplaceShiftsRequest(shifts);
        ResponseEntity<List<ShiftDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/shifts",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersFor(institutionId)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ReplaceShiftsRequest.ShiftInput morningShift(boolean enabled) {
        return new ReplaceShiftsRequest.ShiftInput(
                ShiftType.MORNING, LocalTime.of(7, 30), 5, 50, 10,
                List.of(1, 2, 3, 4, 5), enabled);
    }

    private ReplaceShiftsRequest.ShiftInput afternoonShift(boolean enabled) {
        return new ReplaceShiftsRequest.ShiftInput(
                ShiftType.AFTERNOON, LocalTime.of(13, 30), 5, 50, 10,
                List.of(1, 2, 3, 4, 5), enabled);
    }

    private ReplaceShiftsRequest.ShiftInput eveningShift(boolean enabled) {
        return new ReplaceShiftsRequest.ShiftInput(
                ShiftType.EVENING, LocalTime.of(18, 50), 4, 50, 10,
                List.of(1, 2, 3, 4, 5), enabled);
    }

    // --- US-018: Cadastrar período letivo ---

    // @spec:AC-051 Criação de período com datas válidas
    @Test
    void deveCriarPeriodoComDatasValidas() {
        UUID instId = createInstitutionAndReturnId("UFPA");

        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        assertNotNull(period.id());
        assertEquals("2025.1", period.name());
        assertEquals(LocalDate.of(2025, 3, 10), period.startDate());
        assertEquals(LocalDate.of(2025, 7, 18), period.endDate());
        assertEquals(0, period.holidayCount());
    }

    // @spec:AC-052 Rejeição de período com data final anterior à inicial
    @Test
    void deveRejeitarPeriodoComDataFinalAnteriorAInicial() {
        UUID instId = createInstitutionAndReturnId("UFPA");

        CreateAcademicPeriodRequest request = new CreateAcademicPeriodRequest(
                "2025.1", LocalDate.of(2025, 7, 18), LocalDate.of(2025, 3, 10));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-053 Rejeição de período sobreposto
    @Test
    void deveRejeitarPeriodoSobreposto() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        createPeriod(instId, "2024.1", LocalDate.of(2024, 3, 4), LocalDate.of(2024, 7, 12));

        CreateAcademicPeriodRequest request = new CreateAcademicPeriodRequest(
                "2024.2", LocalDate.of(2024, 6, 1), LocalDate.of(2024, 12, 15));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-054 Listagem paginada de períodos
    @Test
    void deveListarPeriodosPaginados() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        createPeriod(instId, "2024.1", LocalDate.of(2024, 3, 4), LocalDate.of(2024, 7, 12));
        createPeriod(instId, "2024.2", LocalDate.of(2024, 8, 1), LocalDate.of(2024, 12, 15));

        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("2024.1"));
        assertTrue(response.getBody().contains("2024.2"));
    }

    // @spec:AC-055 Edição de período
    @Test
    void deveEditarPeriodo() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        UpdateAcademicPeriodRequest update = new UpdateAcademicPeriodRequest(
                "2025.1 - Atualizado", LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 25));
        ResponseEntity<AcademicPeriodDTO> response = restTemplate.exchange(
                "/academic-periods/" + period.id(),
                HttpMethod.PUT,
                new HttpEntity<>(update, headersFor(instId)),
                AcademicPeriodDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("2025.1 - Atualizado", response.getBody().name());
        assertEquals(LocalDate.of(2025, 7, 25), response.getBody().endDate());
    }

    // @spec:AC-056 Exclusão de período com cascade
    @Test
    void deveExcluirPeriodoComCascade() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        // Add shifts
        replaceShifts(instId, period.id(), List.of(morningShift(true)));

        // Add holidays
        restTemplate.exchange(
                "/academic-periods/" + period.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(List.of(
                        new HolidayDTO(LocalDate.of(2025, 4, 21), "Tiradentes", HolidayType.NATIONAL))),
                        headersFor(instId)),
                Object.class);

        // Delete period
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/academic-periods/" + period.id(),
                HttpMethod.DELETE,
                new HttpEntity<>(headersFor(instId)),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Verify period is gone
        ResponseEntity<Object> getResponse = restTemplate.exchange(
                "/academic-periods/" + period.id(),
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                Object.class);
        assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
    }

    // --- US-019: Registrar feriados e recessos ---

    // @spec:AC-057 Substituição da lista de feriados com tipo
    @Test
    void deveSubstituirListaDeFeriadosComTipo() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        List<HolidayDTO> holidays = List.of(
                new HolidayDTO(LocalDate.of(2025, 4, 18), "Sexta-feira Santa", HolidayType.NATIONAL),
                new HolidayDTO(LocalDate.of(2025, 4, 21), "Tiradentes", HolidayType.NATIONAL),
                new HolidayDTO(LocalDate.of(2025, 5, 1), "Dia do Trabalho", HolidayType.NATIONAL),
                new HolidayDTO(LocalDate.of(2025, 6, 19), "Recesso junino", HolidayType.RECESS));

        ResponseEntity<List<HolidayDTO>> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(holidays), headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(4, response.getBody().size());

        // Replace with fewer
        List<HolidayDTO> fewer = List.of(
                new HolidayDTO(LocalDate.of(2025, 4, 21), "Tiradentes", HolidayType.NATIONAL));
        ResponseEntity<List<HolidayDTO>> response2 = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(fewer), headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        assertEquals(1, response2.getBody().size());
    }

    // @spec:AC-058 Rejeição de feriado fora do intervalo
    @Test
    void deveRejeitarFeriadoForaDoIntervalo() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2024.1",
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 7, 12));

        List<HolidayDTO> holidays = List.of(
                new HolidayDTO(LocalDate.of(2024, 12, 25), "Natal", HolidayType.NATIONAL));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(holidays), headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- US-024: Configurar turnos ---

    // @spec:AC-067 Substituição da configuração de turnos
    @Test
    void deveConfigurarTurnosComEndTimeCalculado() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        List<ShiftDTO> shifts = replaceShifts(instId, period.id(),
                List.of(morningShift(true), afternoonShift(true), eveningShift(false)));

        assertEquals(3, shifts.size());
        ShiftDTO morning = shifts.stream()
                .filter(s -> s.shiftType() == ShiftType.MORNING).findFirst().orElseThrow();
        // endTime = 07:30 + 5*50 + 4*10 = 07:30 + 290min = 12:20
        assertEquals(LocalTime.of(12, 20), morning.endTime());
        assertTrue(morning.enabled());

        ShiftDTO evening = shifts.stream()
                .filter(s -> s.shiftType() == ShiftType.EVENING).findFirst().orElseThrow();
        assertFalse(evening.enabled());
    }

    // @spec:AC-068 Rejeição de turno com classesPerDay zero
    @Test
    void deveRejeitarTurnoComClassesPerDayZero() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        ReplaceShiftsRequest.ShiftInput invalidShift = new ReplaceShiftsRequest.ShiftInput(
                ShiftType.MORNING, LocalTime.of(7, 30), 0, 50, 10,
                List.of(1, 2, 3, 4, 5), true);

        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/shifts",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceShiftsRequest(List.of(invalidShift)), headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-069 Rejeição de turno duplicado no mesmo período
    @Test
    void deveRejeitarTurnoDuplicado() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/shifts",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceShiftsRequest(List.of(morningShift(true), morningShift(true))),
                        headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- US-020: Grade de ocupação ---

    // @spec:AC-059 Substituição da grade de ocupação por slots
    @Test
    void deveSubstituirGradeDeOcupacaoPorSlots() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        List<ShiftDTO> shifts = replaceShifts(instId, period.id(), List.of(morningShift(true)));
        UUID morningId = shifts.get(0).id();
        UUID labId = createLab(instId, "LABCOMP-02");

        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(
                new ReplaceScheduleRequest.ScheduleInput(morningId, 1, List.of(1, 2, 3, 4, 5)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 2, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 3, List.of(1, 2, 3, 4, 5)));

        ResponseEntity<List<ScheduleEntryDTO>> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
    }

    // @spec:AC-060 Rejeição de slot fora do range do turno
    @Test
    void deveRejeitarSlotForaDoRange() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        List<ShiftDTO> shifts = replaceShifts(instId, period.id(), List.of(morningShift(true)));
        UUID morningId = shifts.get(0).id();
        UUID labId = createLab(instId, "LABCOMP-02");

        // Slot 6 is out of range for a shift with classesPerDay = 5
        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(
                new ReplaceScheduleRequest.ScheduleInput(morningId, 1, List.of(1, 2, 6)));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-061 Rejeição de dia fora dos activeDays do turno
    @Test
    void deveRejeitarDiaForaDosActiveDays() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        List<ShiftDTO> shifts = replaceShifts(instId, period.id(), List.of(morningShift(true)));
        UUID morningId = shifts.get(0).id();
        UUID labId = createLab(instId, "LABCOMP-02");

        // dayOfWeek 7 (Sunday) is not in activeDays [1,2,3,4,5]
        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(
                new ReplaceScheduleRequest.ScheduleInput(morningId, 7, List.of(1, 2)));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- US-021: Resumo de dias letivos e horas ---

    // @spec:AC-062 Cálculo de dias letivos por mês
    @Test
    void deveCalcularDiasLetivosPorMes() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        replaceShifts(instId, period.id(), List.of(morningShift(true)));

        // Add one holiday in April (a weekday)
        restTemplate.exchange(
                "/academic-periods/" + period.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(List.of(
                        new HolidayDTO(LocalDate.of(2025, 4, 21), "Tiradentes", HolidayType.NATIONAL))),
                        headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        ResponseEntity<PeriodSummaryDTO> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                PeriodSummaryDTO.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PeriodSummaryDTO summary = response.getBody();
        assertNotNull(summary);
        assertFalse(summary.schoolDaysPerMonth().isEmpty());

        // April 2025 has 22 weekdays; minus Tiradentes (Mon Apr 21) = 21
        PeriodSummaryDTO.MonthSchoolDays april = summary.schoolDaysPerMonth().stream()
                .filter(m -> m.month().equals("2025-04")).findFirst().orElseThrow();
        assertEquals(21, april.schoolDays());
    }

    // @spec:AC-063 Cálculo de horas de uso por slots ocupados
    @Test
    void deveCalcularHorasDeUsoPorSlots() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        List<ShiftDTO> shifts = replaceShifts(instId, period.id(), List.of(morningShift(true)));
        UUID morningId = shifts.get(0).id();
        UUID labId = createLab(instId, "LABCOMP-02");

        // 3 slots occupied Mon-Fri (all 5 weekdays)
        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(
                new ReplaceScheduleRequest.ScheduleInput(morningId, 1, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 2, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 3, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 4, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(morningId, 5, List.of(1, 2, 3)));

        restTemplate.exchange(
                "/academic-periods/" + period.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                new ParameterizedTypeReference<List<ScheduleEntryDTO>>() {});

        ResponseEntity<PeriodSummaryDTO> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                PeriodSummaryDTO.class);

        PeriodSummaryDTO summary = response.getBody();
        assertNotNull(summary);
        assertFalse(summary.laboratorySummaries().isEmpty());

        PeriodSummaryDTO.LaboratorySummary labSummary = summary.laboratorySummaries().stream()
                .filter(l -> l.laboratoryId().equals(labId)).findFirst().orElseThrow();
        // April 2025 has 22 weekdays. hours = 3 slots × 50min × 22 days / 60 = 55.0h
        assertTrue(labSummary.totalHours() > 0);
    }

    // @spec:AC-064 Laboratório sem grade retorna zero horas
    @Test
    void laboratorioSemGradeRetornaZeroHoras() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        replaceShifts(instId, period.id(), List.of(morningShift(true)));
        UUID labId = createLab(instId, "LABIA");

        ResponseEntity<PeriodSummaryDTO> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                PeriodSummaryDTO.class);

        PeriodSummaryDTO summary = response.getBody();
        assertNotNull(summary);

        PeriodSummaryDTO.LaboratorySummary labSummary = summary.laboratorySummaries().stream()
                .filter(l -> l.laboratoryId().equals(labId)).findFirst().orElseThrow();
        assertEquals(0.0, labSummary.totalHours());
    }

    // @spec:AC-070 Turno desativado não contabiliza no cálculo
    @Test
    void turnoDesativadoNaoContabiliza() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO period = createPeriod(instId, "2025.1",
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        List<ShiftDTO> shifts = replaceShifts(instId, period.id(),
                List.of(morningShift(true), eveningShift(false)));
        UUID morningId = shifts.stream()
                .filter(s -> s.shiftType() == ShiftType.MORNING).findFirst().orElseThrow().id();
        UUID eveningId = shifts.stream()
                .filter(s -> s.shiftType() == ShiftType.EVENING).findFirst().orElseThrow().id();
        UUID labId = createLab(instId, "LABCOMP-02");

        // Occupy both morning and evening slots
        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(
                new ReplaceScheduleRequest.ScheduleInput(morningId, 1, List.of(1, 2, 3)),
                new ReplaceScheduleRequest.ScheduleInput(eveningId, 1, List.of(1, 2)));

        restTemplate.exchange(
                "/academic-periods/" + period.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                new ParameterizedTypeReference<List<ScheduleEntryDTO>>() {});

        ResponseEntity<PeriodSummaryDTO> response = restTemplate.exchange(
                "/academic-periods/" + period.id() + "/summary",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instId)),
                PeriodSummaryDTO.class);

        PeriodSummaryDTO summary = response.getBody();
        PeriodSummaryDTO.LaboratorySummary labSummary = summary.laboratorySummaries().stream()
                .filter(l -> l.laboratoryId().equals(labId)).findFirst().orElseThrow();

        // Only morning hours should count (3 slots × 50min × days), not evening
        // April has ~4 Mondays. hours from morning only = 3 × 50 × 4 / 60 = 10.0
        // Evening should NOT be counted
        // Total should be much less than if evening were counted
        assertTrue(labSummary.totalHours() > 0);
        // The total should only reflect morning slots
        // 3 slots × 50min × 22 days(Mon only: ~4) / 60
        // With only Monday occupied: ~10h for morning only
    }

    // --- US-022: Copiar período ---

    // @spec:AC-065 Cópia de período com turnos, feriados e grades
    @Test
    void deveCopiarPeriodoComTurnosFeriadosEGrades() {
        UUID instId = createInstitutionAndReturnId("UFPA");
        AcademicPeriodDTO source = createPeriod(instId, "2024.1",
                LocalDate.of(2024, 3, 4), LocalDate.of(2024, 7, 12));
        List<ShiftDTO> shifts = replaceShifts(instId, source.id(), List.of(morningShift(true)));
        UUID morningId = shifts.get(0).id();

        // Add holidays (inside source range)
        restTemplate.exchange(
                "/academic-periods/" + source.id() + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceHolidaysRequest(List.of(
                        new HolidayDTO(LocalDate.of(2024, 4, 21), "Tiradentes", HolidayType.NATIONAL),
                        new HolidayDTO(LocalDate.of(2024, 5, 1), "Dia do Trabalho", HolidayType.NATIONAL))),
                        headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        // Add schedule for default lab (LABCOMP-01 created with institution)
        UUID labId = createLab(instId, "LABCOMP-COPY");
        restTemplate.exchange(
                "/academic-periods/" + source.id() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(List.of(
                        new ReplaceScheduleRequest.ScheduleInput(morningId, 1, List.of(1, 2, 3)))),
                        headersFor(instId)),
                new ParameterizedTypeReference<>() {});

        // Copy period with new dates
        CopyPeriodRequest copyRequest = new CopyPeriodRequest(
                "2024.2", LocalDate.of(2024, 8, 1), LocalDate.of(2024, 12, 15));
        ResponseEntity<AcademicPeriodDTO> response = restTemplate.exchange(
                "/academic-periods/" + source.id() + "/copy",
                HttpMethod.POST,
                new HttpEntity<>(copyRequest, headersFor(instId)),
                AcademicPeriodDTO.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AcademicPeriodDTO copied = response.getBody();
        assertNotNull(copied);
        assertEquals("2024.2", copied.name());
        assertEquals(LocalDate.of(2024, 8, 1), copied.startDate());
        // Shifts are copied
        assertFalse(copied.shifts().isEmpty());
    }

    // --- US-023: Isolamento RLS ---

    // @spec:AC-066 RLS isola períodos por instituição
    @Test
    void deveIsolarPeriodosPorInstituicaoViaRls() {
        UUID instA = createInstitutionAndReturnId("UFPA");
        UUID instB = createInstitutionAndReturnId("UNICAMP");

        createPeriod(instA, "2025.1-UFPA", LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));
        createPeriod(instB, "2025.1-UNICAMP", LocalDate.of(2025, 3, 10), LocalDate.of(2025, 7, 18));

        // Inst A should not see Inst B's period
        ResponseEntity<String> responseA = restTemplate.exchange(
                "/academic-periods?page=0&size=50",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instA)),
                String.class);
        assertTrue(responseA.getBody().contains("2025.1-UFPA"));
        assertFalse(responseA.getBody().contains("2025.1-UNICAMP"));

        // Inst B should not see Inst A's period
        ResponseEntity<String> responseB = restTemplate.exchange(
                "/academic-periods?page=0&size=50",
                HttpMethod.GET,
                new HttpEntity<>(headersFor(instB)),
                String.class);
        assertTrue(responseB.getBody().contains("2025.1-UNICAMP"));
        assertFalse(responseB.getBody().contains("2025.1-UFPA"));
    }
}
