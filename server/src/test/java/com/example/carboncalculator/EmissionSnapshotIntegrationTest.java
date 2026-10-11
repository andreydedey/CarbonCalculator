package com.example.carboncalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.DayOfWeek;
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

import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.AcceptInviteRequest;
import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.ConfigurationDTO;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.CreateMonitorRequest;
import com.example.carboncalculator.dto.CreateOperatingSystemRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.MonitorDTO;
import com.example.carboncalculator.dto.OperatingSystemDTO;
import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.ShiftType;
import com.example.carboncalculator.services.EmissionPeriodSnapshotService;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EmissionSnapshotIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "app";
    private static final String APP_PASSWORD = "app";
    private static final List<Integer> ALL_SLOTS = List.of(1, 2, 3, 4, 5);

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
    private EmissionPeriodSnapshotService periodSnapshotService;

    @Autowired
    private Clock clock;

    private String adminToken;
    private UUID institutionId;

    private record Scenario(UUID periodId, UUID labId, UUID shiftId) {}

    @BeforeEach
    void setUp() {
        if (adminToken == null) {
            LoginRequest login = new LoginRequest("admin@admin.com", "password");
            ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity(
                    "/auth/login", login, AuthResponse.class);
            assertEquals(HttpStatus.OK, authResp.getStatusCode());
            adminToken = authResp.getBody().accessToken();
        }

        institutionId = createInstitution("TEST-" + System.nanoTime()).id();
    }

    // ========================= HTTP helpers =========================

    private HttpHeaders headersFor(UUID instId) {
        return headersFor(instId, adminToken);
    }

    private HttpHeaders headersFor(UUID instId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, instId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(token);
        return headers;
    }

    private InstitutionDTO createInstitution(String acronym) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronym, acronym, "Cidade", "PA");
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST, new HttpEntity<>(request, headers), InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private <T> T post(UUID instId, String url, Object body, Class<T> type) {
        ResponseEntity<T> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headersFor(instId)), type);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private UUID createLabWithStations(UUID instId, int stations) {
        String suffix = String.valueOf(System.nanoTime());
        UUID labId = post(instId, "/laboratories",
                new CreateLaboratoryRequest("LAB-" + suffix, null), LaboratoryDTO.class).id();
        UUID modelId = post(instId, "/equipment-models", new CreateEquipmentModelRequest(
                "Desktop-" + suffix, "DESKTOP", "Intel i5", 65, 4, 8, null, null, false, null),
                EquipmentModelDTO.class).id();
        UUID monitorId = post(instId, "/monitors",
                new CreateMonitorRequest("Monitor-" + suffix, 21), MonitorDTO.class).id();
        UUID osId = post(instId, "/operating-systems",
                new CreateOperatingSystemRequest("OS-" + suffix), OperatingSystemDTO.class).id();
        UUID configId = post(instId, "/configurations",
                new CreateConfigurationRequest(modelId, osId, monitorId), ConfigurationDTO.class).id();
        post(instId, "/laboratories/" + labId + "/equipment",
                new CreateLaboratoryEquipmentRequest(configId, stations), LaboratoryEquipmentDTO.class);
        return labId;
    }

    private void createEmissionFactors(UUID instId, LocalDate start, LocalDate end, String value) {
        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
            post(instId, "/emission-factors",
                    new CreateEmissionFactorRequest(month, new BigDecimal(value), "MCTI — SIN " + month),
                    String.class);
        }
    }

    /** One lab with 10 stations, a Mon-Fri morning shift of 5 classes and every slot occupied. */
    private Scenario createScenario(UUID instId, LocalDate start, LocalDate end) {
        UUID labId = createLabWithStations(instId, 10);
        UUID periodId = post(instId, "/academic-periods",
                new CreateAcademicPeriodRequest("P-" + System.nanoTime(), start, end), AcademicPeriodDTO.class).id();

        ResponseEntity<List<ShiftDTO>> shifts = restTemplate.exchange(
                "/academic-periods/" + periodId + "/shifts", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceShiftsRequest(List.of(new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 5, 50, 10, List.of(1, 2, 3, 4, 5), true))),
                        headersFor(instId)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, shifts.getStatusCode());

        Scenario scenario = new Scenario(periodId, labId, shifts.getBody().get(0).id());
        replaceSchedule(instId, scenario, ALL_SLOTS);
        return scenario;
    }

    private void replaceSchedule(UUID instId, Scenario scenario, List<Integer> slots) {
        replaceSchedule(instId, scenario, scenario.labId(), slots);
    }

    private void replaceSchedule(UUID instId, Scenario scenario, UUID labId, List<Integer> slots) {
        List<ReplaceScheduleRequest.ScheduleInput> entries = List.of(1, 2, 3, 4, 5).stream()
                .map(day -> new ReplaceScheduleRequest.ScheduleInput(scenario.shiftId(), day, slots))
                .toList();
        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods/" + scenario.periodId() + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(new ReplaceScheduleRequest(entries), headersFor(instId)),
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String registerResearcherAndGetToken(String email) {
        ResponseEntity<UserMemberDTO> inviteResp = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(new InviteRequest(email, "RESEARCHER"), headersFor(institutionId)),
                UserMemberDTO.class);
        assertEquals(HttpStatus.CREATED, inviteResp.getStatusCode());
        String rawToken = inviteResp.getBody().inviteLink().replace("/register?token=", "");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/invitations/{token}/accept",
                new AcceptInviteRequest("Pesquisador Teste", "Senha-123!"), AuthResponse.class, rawToken);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().accessToken();
    }

    private List<SnapshotAggregateDTO> getSnapshots(UUID instId, String granularity) {
        return getSnapshots(instId, adminToken, granularity, null, null);
    }

    private List<SnapshotAggregateDTO> getSnapshots(UUID instId, String token, String granularity,
            LocalDate startDate, LocalDate endDate) {
        return getSnapshots(instId, token, granularity, startDate, endDate, null);
    }

    private List<SnapshotAggregateDTO> getSnapshots(UUID instId, String token, String granularity,
            LocalDate startDate, LocalDate endDate, UUID laboratoryId) {
        StringBuilder url = new StringBuilder("/snapshots?granularity=").append(granularity);
        if (startDate != null) url.append("&startDate=").append(startDate);
        if (endDate != null) url.append("&endDate=").append(endDate);
        if (laboratoryId != null) url.append("&laboratoryId=").append(laboratoryId);
        ResponseEntity<List<SnapshotAggregateDTO>> response = restTemplate.exchange(
                url.toString(), HttpMethod.GET,
                new HttpEntity<>(headersFor(instId, token)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private BigDecimal periodTotal(UUID instId) {
        List<SnapshotAggregateDTO> periods = getSnapshots(instId, "period");
        assertEquals(1, periods.size());
        return periods.get(0).totalEmissionKg();
    }

    private static BigDecimal total(List<SnapshotAggregateDTO> buckets) {
        return buckets.stream().map(SnapshotAggregateDTO::totalEmissionKg).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int capture(UUID instId) {
        return periodSnapshotService.captureEndedPeriods(instId, LocalDate.now(clock));
    }

    private Scenario endedMarch2025(UUID instId) {
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2025, 3, 31);
        createEmissionFactors(instId, start, end, "0.0500");
        return createScenario(instId, start, end);
    }

    // ========================= Live and frozen series =========================

    // @spec:AC-171 Período em andamento é calculado sob demanda e reflete alterações na grade
    @Test
    void devePeriodoEmAndamentoRefletirAlteracaoNaGrade() {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(21);
        LocalDate end = today.plusDays(60);
        createEmissionFactors(institutionId, start, end, "0.0500");
        Scenario scenario = createScenario(institutionId, start, end);

        BigDecimal fullGrid = periodTotal(institutionId);
        assertTrue(fullGrid.signum() > 0);

        replaceSchedule(institutionId, scenario, List.of(1));

        BigDecimal reducedGrid = periodTotal(institutionId);
        assertEquals(fullGrid.doubleValue() / 5, reducedGrid.doubleValue(), 0.01, "one of five classes left");
        assertEquals(0, capture(institutionId), "a running period is never frozen");
    }

    // @spec:AC-172 Período encerrado é congelado e alterações posteriores não mudam a série
    @Test
    void devePeriodoEncerradoFicarCongeladoAposOSnapshot() {
        Scenario scenario = endedMarch2025(institutionId);
        BigDecimal beforeSnapshot = periodTotal(institutionId);

        assertEquals(1, capture(institutionId));
        assertEquals(0, beforeSnapshot.compareTo(periodTotal(institutionId)));

        replaceSchedule(institutionId, scenario, List.of(1));

        assertEquals(0, beforeSnapshot.compareTo(periodTotal(institutionId)));
    }

    // @spec:AC-173 Congelamento é adiado enquanto faltar fator de emissão de algum mês do período
    @Test
    void deveAdiarSnapshotQuandoFaltaFatorDeEmissao() {
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2025, 4, 30);
        createEmissionFactors(institutionId, start, start, "0.0500");
        createScenario(institutionId, start, end);

        assertEquals(0, capture(institutionId));

        createEmissionFactors(institutionId, end, end, "0.0500");

        assertEquals(1, capture(institutionId));
    }

    // @spec:AC-174 Congelamento é idempotente
    @Test
    void deveSnapshotSerIdempotente() {
        endedMarch2025(institutionId);

        assertEquals(1, capture(institutionId));
        assertEquals(0, capture(institutionId));
    }

    // @spec:AC-175 Granularidades são derivadas dos mesmos dias, congelados ou calculados
    @Test
    void deveGranularidadesSomaremOMesmoTotal() {
        endedMarch2025(institutionId);
        capture(institutionId);

        BigDecimal periodTotal = periodTotal(institutionId);
        assertEquals(0, periodTotal.compareTo(total(getSnapshots(institutionId, "monthly"))));
        assertEquals(0, periodTotal.compareTo(total(getSnapshots(institutionId, "weekly"))));
        assertEquals(0, periodTotal.compareTo(total(getSnapshots(institutionId, "daily"))));
    }

    // ========================= Laboratory filter =========================

    private void assertLaboratoryFilterSplitsTotal(Scenario scenario, UUID otherLabId) {
        BigDecimal all = periodTotal(institutionId);
        BigDecimal fullGridLab = total(getSnapshots(institutionId, adminToken, "period", null, null, scenario.labId()));
        BigDecimal oneSlotLab = total(getSnapshots(institutionId, adminToken, "period", null, null, otherLabId));

        assertTrue(oneSlotLab.signum() > 0);
        assertEquals(fullGridLab.doubleValue() / 5, oneSlotLab.doubleValue(), 0.01);
        assertEquals(0, all.compareTo(fullGridLab.add(oneSlotLab)));
    }

    // @spec:AC-176 Filtro por laboratório restringe a série às aulas do laboratório
    @Test
    void deveFiltrarPorLaboratorioEmPeriodoEmAndamento() {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(21);
        LocalDate end = today.plusDays(60);
        createEmissionFactors(institutionId, start, end, "0.0500");
        Scenario scenario = createScenario(institutionId, start, end);
        UUID otherLabId = createLabWithStations(institutionId, 10);
        replaceSchedule(institutionId, scenario, otherLabId, List.of(1));

        assertLaboratoryFilterSplitsTotal(scenario, otherLabId);
    }

    // @spec:AC-176 Filtro por laboratório restringe a série às aulas do laboratório
    @Test
    void deveFiltrarPorLaboratorioEmPeriodoCongelado() {
        Scenario scenario = endedMarch2025(institutionId);
        UUID otherLabId = createLabWithStations(institutionId, 10);
        replaceSchedule(institutionId, scenario, otherLabId, List.of(1));
        assertEquals(1, capture(institutionId));

        assertLaboratoryFilterSplitsTotal(scenario, otherLabId);

        List<SnapshotAggregateDTO> labDays = getSnapshots(institutionId, adminToken, "daily", null, null, otherLabId);
        List<SnapshotAggregateDTO> allDays = getSnapshots(institutionId, "daily");
        assertEquals(allDays.size(), labDays.size(), "every school day is listed for the laboratory");
    }

    // ========================= Aggregation =========================

    // @spec:AC-136 Agregação mensal soma corretamente dias do mesmo mês
    @Test
    void deveAgregarMensalmenteSomandoDiasDoMesmoMes() {
        endedMarch2025(institutionId);

        List<SnapshotAggregateDTO> daily = getSnapshots(institutionId, "daily");
        List<SnapshotAggregateDTO> monthly = getSnapshots(institutionId, "monthly");

        assertEquals(1, monthly.size());
        assertEquals(0, total(daily).compareTo(monthly.get(0).totalEmissionKg()));
        assertEquals(daily.size(), monthly.get(0).schoolDays());
    }

    // @spec:AC-137 Agregação por período letivo soma todos os dias do período
    @Test
    void deveAgregarPorPeriodoSomandoTodosOsDias() {
        Scenario scenario = endedMarch2025(institutionId);

        List<SnapshotAggregateDTO> periods = getSnapshots(institutionId, "period");

        assertEquals(1, periods.size());
        assertEquals(scenario.periodId(), periods.get(0).periodId());
        assertEquals(0, total(getSnapshots(institutionId, "daily")).compareTo(periods.get(0).totalEmissionKg()));
    }

    // @spec:AC-138 Agregação semanal agrupa por semana ISO
    @Test
    void deveAgregarSemanalmentePorSemanaIso() {
        endedMarch2025(institutionId);

        List<SnapshotAggregateDTO> weekly = getSnapshots(institutionId, "weekly");

        assertFalse(weekly.isEmpty());
        for (SnapshotAggregateDTO week : weekly) {
            assertEquals(DayOfWeek.MONDAY, week.startDate().getDayOfWeek());
            assertEquals(week.startDate().plusDays(6), week.endDate());
            assertTrue(week.schoolDays() <= 5);
        }
    }

    // @spec:AC-139 Granularidade diária retorna um registro por dia letivo
    @Test
    void deveRetornarUmRegistroPorDiaNaGranularidadeDiaria() {
        endedMarch2025(institutionId);

        List<SnapshotAggregateDTO> daily = getSnapshots(institutionId, "daily");

        assertFalse(daily.isEmpty());
        assertEquals(daily.size(), daily.stream().map(SnapshotAggregateDTO::startDate).distinct().count());
        for (SnapshotAggregateDTO day : daily) {
            assertEquals(day.startDate(), day.endDate());
            assertTrue(day.startDate().getDayOfWeek().getValue() <= 5);
        }
    }

    // @spec:AC-140 variationPct calculado em relação ao registro imediatamente anterior
    // @spec:AC-141 Primeiro registro da série tem variationPct null
    @Test
    void deveCalcularVariationPctEmRelacaoAoRegistroAnterior() {
        endedMarch2025(institutionId);

        List<SnapshotAggregateDTO> weekly = getSnapshots(institutionId, "weekly");

        assertTrue(weekly.size() > 1);
        assertNull(weekly.get(0).variationPct());
        SnapshotAggregateDTO previous = weekly.get(0);
        SnapshotAggregateDTO current = weekly.get(1);
        double expected = (current.totalEmissionKg().doubleValue() - previous.totalEmissionKg().doubleValue())
                / previous.totalEmissionKg().doubleValue() * 100;
        assertNotNull(current.variationPct());
        assertEquals(expected, current.variationPct().doubleValue(), 0.01);
    }

    // @spec:AC-142 RLS: snapshots de outra instituição não são retornados
    @Test
    void deveIsolarSnapshotsPorInstituicaoViaRls() {
        UUID otherInstitutionId = createInstitution("OTH-" + System.nanoTime()).id();
        endedMarch2025(otherInstitutionId);
        capture(otherInstitutionId);

        assertFalse(getSnapshots(otherInstitutionId, "monthly").isEmpty());
        assertTrue(getSnapshots(institutionId, "monthly").isEmpty());
    }

    // @spec:AC-143 Filtro startDate/endDate exclui registros fora do intervalo
    @Test
    void deveFiltrarPorStartDateEEndDateExcluindoForaDoIntervalo() {
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2025, 5, 31);
        createEmissionFactors(institutionId, start, end, "0.0500");
        createScenario(institutionId, start, end);

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly",
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

        assertEquals(1, result.size());
        assertEquals(YearMonth.of(2025, 4), YearMonth.from(result.get(0).startDate()));
    }

    // @spec:AC-144 RESEARCHER consegue consultar snapshots
    @Test
    void deveResearcherConseguirConsultarSnapshots() {
        String researcherToken = registerResearcherAndGetToken("researcher-" + System.nanoTime() + "@example.com");

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, researcherToken, "monthly", null, null);

        assertNotNull(result);
    }
}
