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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

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
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.AcademicPeriodDTO;
import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.ConfigurationDTO;
import com.example.carboncalculator.dto.CreateAcademicPeriodRequest;
import com.example.carboncalculator.dto.CreateConfigurationRequest;
import com.example.carboncalculator.dto.CreateEmissionFactorRequest;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.dto.HolidayDTO;
import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.dto.InviteRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.RegisterRequest;
import com.example.carboncalculator.dto.ReplaceHolidaysRequest;
import com.example.carboncalculator.dto.ReplaceScheduleRequest;
import com.example.carboncalculator.dto.ReplaceShiftsRequest;
import com.example.carboncalculator.dto.ShiftDTO;
import com.example.carboncalculator.dto.SnapshotAggregateDTO;
import com.example.carboncalculator.entities.AcademicPeriod;
import com.example.carboncalculator.entities.EmissionSnapshot;
import com.example.carboncalculator.entities.HolidayType;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.ShiftType;
import com.example.carboncalculator.repositories.AcademicPeriodRepository;
import com.example.carboncalculator.repositories.EmissionSnapshotRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.services.EmissionSnapshotCronService;

/**
 * Integration tests for the longitudinal tracking feature (US-033, US-034):
 * the daily cron capture (EmissionSnapshotCronService) and the aggregated
 * query API (GET /snapshots, backed by EmissionSnapshotQueryService).
 *
 * <p>Query scenarios seed {@link EmissionSnapshot} rows directly through the
 * repository (there is no create endpoint by design — snapshots are
 * cron-only and immutable) inside a manually tenant-scoped transaction that
 * mirrors what {@code TenantFilter} does for HTTP requests. Cron scenarios
 * invoke {@link EmissionSnapshotCronService#captureYesterday()} directly and
 * then inspect the persisted result the same way.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EmissionSnapshotIntegrationTest {

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EmissionSnapshotRepository snapshotRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;

    @Autowired
    private EmissionSnapshotCronService cronService;

    private JdbcTemplate jdbcTemplate;

    // --- Shared test state ---
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

        InstitutionDTO institution = createInstitution("TEST-" + System.nanoTime());
        institutionId = institution.id();
    }

    // ========================= HTTP helpers =========================

    private HttpHeaders headersFor(UUID instId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, instId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private HttpHeaders headersFor(UUID instId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, instId.toString());
        headers.set("Content-Type", "application/json");
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return headers;
    }

    private InstitutionDTO createInstitution(String acronym) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronym, acronym, "Cidade", "PA");
        ResponseEntity<InstitutionDTO> response = restTemplate.exchange(
                "/institutions", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                InstitutionDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
    }

    private UUID createLab(UUID instId, String name) {
        ResponseEntity<LaboratoryDTO> response = restTemplate.exchange(
                "/laboratories", HttpMethod.POST,
                new HttpEntity<>(new CreateLaboratoryRequest(name, null), headersFor(instId)),
                LaboratoryDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private UUID createEquipmentModel(UUID instId, String name, int tdpWatts) {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                name, "DESKTOP", "Intel i5", tdpWatts, 4, 8, null, null, false, null);
        ResponseEntity<EquipmentModelDTO> response = restTemplate.exchange(
                "/equipment-models", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                EquipmentModelDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private UUID createConfiguration(UUID instId, UUID equipmentModelId, String os) {
        CreateConfigurationRequest request = new CreateConfigurationRequest(equipmentModelId, os, null);
        ResponseEntity<ConfigurationDTO> response = restTemplate.exchange(
                "/configurations", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                ConfigurationDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private void assignEquipment(UUID instId, UUID labId, UUID configId, int quantity) {
        CreateLaboratoryEquipmentRequest request = new CreateLaboratoryEquipmentRequest(configId, quantity);
        ResponseEntity<LaboratoryEquipmentDTO> response = restTemplate.exchange(
                "/laboratories/" + labId + "/equipment", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                LaboratoryEquipmentDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private UUID createPeriod(UUID instId, String name, LocalDate start, LocalDate end) {
        CreateAcademicPeriodRequest request = new CreateAcademicPeriodRequest(name, start, end);
        ResponseEntity<AcademicPeriodDTO> response = restTemplate.exchange(
                "/academic-periods", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)),
                AcademicPeriodDTO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().id();
    }

    private List<ShiftDTO> replaceShifts(UUID instId, UUID periodId, List<ReplaceShiftsRequest.ShiftInput> shifts) {
        ReplaceShiftsRequest request = new ReplaceShiftsRequest(shifts);
        ResponseEntity<List<ShiftDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/shifts",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersFor(instId)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private void replaceSchedule(UUID instId, UUID periodId, UUID labId,
            List<ReplaceScheduleRequest.ScheduleInput> entries) {
        ReplaceScheduleRequest request = new ReplaceScheduleRequest(entries);
        ResponseEntity<String> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/laboratories/" + labId + "/schedule",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersFor(instId)),
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private void replaceHolidays(UUID instId, UUID periodId, List<HolidayDTO> holidays) {
        ReplaceHolidaysRequest request = new ReplaceHolidaysRequest(holidays);
        ResponseEntity<List<HolidayDTO>> response = restTemplate.exchange(
                "/academic-periods/" + periodId + "/holidays",
                HttpMethod.PUT,
                new HttpEntity<>(request, headersFor(instId)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private void createEmissionFactor(UUID instId, YearMonth month, String value) {
        CreateEmissionFactorRequest request = new CreateEmissionFactorRequest(
                month, new BigDecimal(value), "MCTI — SIN " + month);
        ResponseEntity<String> response = restTemplate.exchange(
                "/emission-factors", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest("Pesquisador Teste", email, "Senha-123!");
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/auth/register", request, AuthResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody().accessToken();
    }

    private void inviteMember(UUID instId, String email, String role) {
        InviteRequest request = new InviteRequest(email, role);
        ResponseEntity<String> response = restTemplate.exchange(
                "/users/invite", HttpMethod.POST,
                new HttpEntity<>(request, headersFor(instId)), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private List<SnapshotAggregateDTO> getSnapshots(UUID instId, String token, String granularity,
            LocalDate startDate, LocalDate endDate) {
        StringBuilder url = new StringBuilder("/snapshots?granularity=").append(granularity);
        if (startDate != null) {
            url.append("&startDate=").append(startDate);
        }
        if (endDate != null) {
            url.append("&endDate=").append(endDate);
        }
        ResponseEntity<List<SnapshotAggregateDTO>> response = restTemplate.exchange(
                url.toString(), HttpMethod.GET,
                new HttpEntity<>(headersFor(instId, token)),
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    // ========================= Direct repository helpers =========================
    // There is no snapshot-creation endpoint by design (US-034: cron-only, immutable).
    // Query scenarios need deterministic historical dates that the cron (which always
    // targets "yesterday") cannot produce, so we seed rows directly through the
    // repository inside a manually tenant-scoped transaction — replicating exactly
    // what TenantFilter does for HTTP requests (SELECT set_config(...) then the JPA
    // call in the same transaction/connection).

    private <T> T runTenantScoped(UUID instId, java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.current_institution', ?, true)", String.class, instId.toString());
            return work.get();
        });
    }

    private EmissionSnapshot seedSnapshot(UUID instId, UUID periodId, LocalDate date,
            String emissionKg, String energyKwh, String factor, boolean schoolDay, int stationCount) {
        return runTenantScoped(instId, () -> {
            Institution institution = institutionRepository.findById(instId).orElseThrow();
            AcademicPeriod period = academicPeriodRepository.findById(periodId).orElseThrow();
            EmissionSnapshot snapshot = EmissionSnapshot.builder()
                    .institution(institution)
                    .academicPeriod(period)
                    .snapshotDate(date)
                    .dayOfWeek(date.getDayOfWeek())
                    .schoolDay(schoolDay)
                    .dailyEmissionKg(new BigDecimal(emissionKg))
                    .dailyEnergyKwh(new BigDecimal(energyKwh))
                    .emissionFactorValue(new BigDecimal(factor))
                    .stationCount(stationCount)
                    .build();
            return snapshotRepository.save(snapshot);
        });
    }

    private Optional<EmissionSnapshot> fetchSnapshot(UUID instId, LocalDate date) {
        return runTenantScoped(instId, () -> snapshotRepository
                .findAll(EmissionSnapshotRepository.withinDateRange(date, date), Sort.unsorted())
                .stream().findFirst());
    }

    private boolean snapshotExists(UUID instId, LocalDate date) {
        return Boolean.TRUE.equals(runTenantScoped(instId, () -> snapshotRepository.existsBySnapshotDate(date)));
    }

    // ========================= QUERY TESTS (US-033) =========================

    // @spec:AC-103 Agregação mensal soma corretamente dias do mesmo mês
    @Test
    void deveAgregarMensalmenteSomandoDiasDoMesmoMes() {
        UUID periodId = createPeriod(institutionId, "2025.2-" + System.nanoTime(),
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 15));

        int[] values = {100, 120, 90, 110, 130};
        LocalDate day = LocalDate.of(2025, 10, 6);
        for (int v : values) {
            seedSnapshot(institutionId, periodId, day, String.valueOf(v), String.valueOf(v * 20),
                    "0.0500", true, 10);
            day = day.plusDays(1);
        }

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly", null, null);

        assertEquals(1, result.size());
        SnapshotAggregateDTO october = result.get(0);
        assertEquals(0, new BigDecimal("550").compareTo(october.totalEmissionKg()));
        assertEquals(5, october.schoolDays());
    }

    // @spec:AC-104 Agregação por período letivo soma todos os dias do período
    @Test
    void deveAgregarPorPeriodoSomandoTodosOsDias() {
        UUID periodId = createPeriod(institutionId, "2025.2-" + System.nanoTime(),
                LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 15));

        BigDecimal expectedTotal = BigDecimal.ZERO;
        LocalDate[] dates = {
                LocalDate.of(2025, 8, 10), LocalDate.of(2025, 9, 15),
                LocalDate.of(2025, 10, 20), LocalDate.of(2025, 11, 25), LocalDate.of(2025, 12, 1)
        };
        for (LocalDate date : dates) {
            seedSnapshot(institutionId, periodId, date, "50", "1000", "0.0500", true, 10);
            expectedTotal = expectedTotal.add(new BigDecimal("50"));
        }

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "period", null, null);

        assertEquals(1, result.size());
        SnapshotAggregateDTO period = result.get(0);
        assertEquals(periodId, period.periodId());
        assertEquals(0, expectedTotal.compareTo(period.totalEmissionKg()));
    }

    // @spec:AC-105 Agregação semanal agrupa por semana ISO
    @Test
    void deveAgregarSemanalmentePorSemanaIso() {
        UUID periodId = createPeriod(institutionId, "2025.W-" + System.nanoTime(),
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30));

        // Monday 2025-11-03 through Friday 2025-11-07 — same ISO week
        LocalDate monday = LocalDate.of(2025, 11, 3);
        for (int i = 0; i < 5; i++) {
            seedSnapshot(institutionId, periodId, monday.plusDays(i), "20", "400", "0.0500", true, 10);
        }

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "weekly", null, null);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("100").compareTo(result.get(0).totalEmissionKg()));
    }

    // @spec:AC-106 Granularidade diária retorna um registro por snapshot_date
    @Test
    void deveRetornarUmRegistroPorDataNaGranularidadeDiaria() {
        UUID periodId = createPeriod(institutionId, "2025.D-" + System.nanoTime(),
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31));

        Set<LocalDate> expectedDates = Set.of(
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 2), LocalDate.of(2025, 12, 3));
        for (LocalDate date : expectedDates) {
            seedSnapshot(institutionId, periodId, date, "40", "800", "0.0500", true, 10);
        }

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "daily", null, null);

        assertEquals(3, result.size());
        Set<LocalDate> actualDates = result.stream()
                .map(SnapshotAggregateDTO::startDate)
                .collect(Collectors.toSet());
        assertEquals(expectedDates, actualDates);
    }

    // @spec:AC-107 variationPct calculado em relação ao registro imediatamente anterior
    @Test
    void deveCalcularVariationPctEmRelacaoAoRegistroAnterior() {
        UUID periodId = createPeriod(institutionId, "2025.V-" + System.nanoTime(),
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 31));

        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 9, 15), "1000", "20000", "0.0500", true, 10);
        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 10, 15), "1120", "22400", "0.0500", true, 10);

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly", null, null);

        assertEquals(2, result.size());
        SnapshotAggregateDTO october = result.get(1);
        assertNotNull(october.variationPct());
        assertEquals(12.0, october.variationPct().doubleValue(), 0.01);
    }

    // @spec:AC-108 Primeiro registro da série tem variationPct null
    @Test
    void devePrimeiroRegistroDaSerieTerVariationPctNulo() {
        UUID periodId = createPeriod(institutionId, "2025.F-" + System.nanoTime(),
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30));

        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 11, 10), "300", "6000", "0.0500", true, 10);

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly", null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).variationPct());
    }

    // @spec:AC-109 RLS: snapshots de outra instituição não são retornados
    @Test
    void deveIsolarSnapshotsPorInstituicaoViaRls() {
        InstitutionDTO otherInstitution = createInstitution("OTH-" + System.nanoTime());
        UUID otherPeriodId = createPeriod(otherInstitution.id(), "2025.O-" + System.nanoTime(),
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30));
        seedSnapshot(otherInstitution.id(), otherPeriodId, LocalDate.of(2025, 11, 10),
                "500", "10000", "0.0500", true, 10);

        // institutionId (from @BeforeEach) has no snapshots at all
        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly", null, null);

        assertTrue(result.isEmpty());
    }

    // @spec:AC-110 Filtro startDate/endDate exclui registros fora do intervalo
    @Test
    void deveFiltrarPorStartDateEEndDateExcluindoForaDoIntervalo() {
        UUID periodId = createPeriod(institutionId, "2025.R-" + System.nanoTime(),
                LocalDate.of(2025, 3, 1), LocalDate.of(2025, 5, 31));

        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 3, 10), "100", "2000", "0.0500", true, 10);
        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 4, 10), "200", "4000", "0.0500", true, 10);
        seedSnapshot(institutionId, periodId, LocalDate.of(2025, 5, 10), "300", "6000", "0.0500", true, 10);

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, adminToken, "monthly",
                LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("200").compareTo(result.get(0).totalEmissionKg()));
    }

    // @spec:AC-111 RESEARCHER consegue consultar snapshots
    @Test
    void deveResearcherConseguirConsultarSnapshots() {
        String email = "researcher-" + System.nanoTime() + "@example.com";
        String researcherToken = registerAndGetToken(email);
        inviteMember(institutionId, email, "RESEARCHER");

        List<SnapshotAggregateDTO> result = getSnapshots(institutionId, researcherToken, "monthly", null, null);

        assertNotNull(result);
    }

    // ========================= CRON TESTS (US-034) =========================

    // @spec:AC-112 Cron cria snapshot para dia dentro de período ativo com fator disponível
    @Test
    void deveCronCriarSnapshotParaDiaDentroDePeriodoAtivoComFatorDisponivel() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int dow = yesterday.getDayOfWeek().getValue();

        createEmissionFactor(institutionId, YearMonth.from(yesterday), "0.0500");
        UUID labId = createLab(institutionId, "LAB-CRON-" + System.nanoTime());
        UUID modelId = createEquipmentModel(institutionId, "Model-" + System.nanoTime(), 100);
        UUID configId = createConfiguration(institutionId, modelId, "Linux");
        assignEquipment(institutionId, labId, configId, 3);

        UUID periodId = createPeriod(institutionId, "PERIOD-CRON-" + System.nanoTime(),
                yesterday.minusDays(5), yesterday.plusDays(5));
        List<ShiftDTO> shifts = replaceShifts(institutionId, periodId, List.of(
                new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 1, 50, 10, List.of(dow), true)));
        replaceSchedule(institutionId, periodId, labId, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shifts.get(0).id(), dow, List.of(1))));

        cronService.captureYesterday();

        Optional<EmissionSnapshot> snapshot = fetchSnapshot(institutionId, yesterday);
        assertTrue(snapshot.isPresent());
        assertEquals(yesterday, snapshot.get().getSnapshotDate());
        assertTrue(snapshot.get().getDailyEmissionKg().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, new BigDecimal("0.0500").compareTo(snapshot.get().getEmissionFactorValue()));
    }

    // @spec:AC-113 Cron é idempotente — execução duplicada não cria novo snapshot
    @Test
    void deveCronSerIdempotente() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int dow = yesterday.getDayOfWeek().getValue();

        createEmissionFactor(institutionId, YearMonth.from(yesterday), "0.0500");
        UUID labId = createLab(institutionId, "LAB-IDEMP-" + System.nanoTime());
        UUID modelId = createEquipmentModel(institutionId, "Model-" + System.nanoTime(), 100);
        UUID configId = createConfiguration(institutionId, modelId, "Linux");
        assignEquipment(institutionId, labId, configId, 2);

        UUID periodId = createPeriod(institutionId, "PERIOD-IDEMP-" + System.nanoTime(),
                yesterday.minusDays(5), yesterday.plusDays(5));
        List<ShiftDTO> shifts = replaceShifts(institutionId, periodId, List.of(
                new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 1, 50, 10, List.of(dow), true)));
        replaceSchedule(institutionId, periodId, labId, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shifts.get(0).id(), dow, List.of(1))));

        cronService.captureYesterday();
        EmissionSnapshot first = fetchSnapshot(institutionId, yesterday).orElseThrow();

        cronService.captureYesterday();
        EmissionSnapshot second = fetchSnapshot(institutionId, yesterday).orElseThrow();

        assertEquals(first.getId(), second.getId());
        assertEquals(0, first.getDailyEmissionKg().compareTo(second.getDailyEmissionKg()));
    }

    // @spec:AC-114 Cron não cria snapshot para dia fora de período letivo
    @Test
    void deveCronNaoCriarSnapshotParaDiaForaDePeriodoLetivo() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        createEmissionFactor(institutionId, YearMonth.from(yesterday), "0.0500");
        // No AcademicPeriod created at all for this institution

        cronService.captureYesterday();

        assertFalse(snapshotExists(institutionId, yesterday));
    }

    // @spec:AC-115 Cron não cria snapshot quando fator SIN está ausente
    @Test
    void deveCronNaoCriarSnapshotQuandoFatorSinAusente() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int dow = yesterday.getDayOfWeek().getValue();
        // No EmissionFactor created for yesterday's month

        UUID labId = createLab(institutionId, "LAB-NOFATOR-" + System.nanoTime());
        UUID modelId = createEquipmentModel(institutionId, "Model-" + System.nanoTime(), 100);
        UUID configId = createConfiguration(institutionId, modelId, "Linux");
        assignEquipment(institutionId, labId, configId, 2);

        UUID periodId = createPeriod(institutionId, "PERIOD-NOFATOR-" + System.nanoTime(),
                yesterday.minusDays(5), yesterday.plusDays(5));
        List<ShiftDTO> shifts = replaceShifts(institutionId, periodId, List.of(
                new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 1, 50, 10, List.of(dow), true)));
        replaceSchedule(institutionId, periodId, labId, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shifts.get(0).id(), dow, List.of(1))));

        cronService.captureYesterday();

        assertFalse(snapshotExists(institutionId, yesterday));
    }

    // @spec:AC-116 Dia feriado gera snapshot com emissão zero
    @Test
    void deveDiaFeriadoGerarSnapshotComEmissaoZero() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int dow = yesterday.getDayOfWeek().getValue();

        createEmissionFactor(institutionId, YearMonth.from(yesterday), "0.0500");
        UUID labId = createLab(institutionId, "LAB-FERIADO-" + System.nanoTime());
        UUID modelId = createEquipmentModel(institutionId, "Model-" + System.nanoTime(), 100);
        UUID configId = createConfiguration(institutionId, modelId, "Linux");
        assignEquipment(institutionId, labId, configId, 2);

        UUID periodId = createPeriod(institutionId, "PERIOD-FERIADO-" + System.nanoTime(),
                yesterday.minusDays(5), yesterday.plusDays(5));
        List<ShiftDTO> shifts = replaceShifts(institutionId, periodId, List.of(
                new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 1, 50, 10, List.of(dow), true)));
        replaceSchedule(institutionId, periodId, labId, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shifts.get(0).id(), dow, List.of(1))));
        replaceHolidays(institutionId, periodId, List.of(
                new HolidayDTO(yesterday, "Feriado de Teste", HolidayType.NATIONAL)));

        cronService.captureYesterday();

        EmissionSnapshot snapshot = fetchSnapshot(institutionId, yesterday).orElseThrow();
        assertFalse(snapshot.isSchoolDay());
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.getDailyEmissionKg()));
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.getDailyEnergyKwh()));
    }

    // @spec:AC-117 Lab sem schedule para o dayOfWeek do dia não contribui para emissão
    @Test
    void deveLabSemScheduleParaDiaNaoContribuirParaEmissao() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int yesterdayDow = yesterday.getDayOfWeek().getValue();
        int otherDow = (yesterdayDow % 7) + 1; // guaranteed different from yesterdayDow

        createEmissionFactor(institutionId, YearMonth.from(yesterday), "0.0500");

        // Lab A: schedule only for a day that is NOT yesterday — must not contribute
        UUID labA = createLab(institutionId, "LAB-A-" + System.nanoTime());
        UUID modelA = createEquipmentModel(institutionId, "Model-A-" + System.nanoTime(), 100);
        UUID configA = createConfiguration(institutionId, modelA, "Linux");
        assignEquipment(institutionId, labA, configA, 5);

        // Lab B: schedule for yesterday's day of week — must contribute
        UUID labB = createLab(institutionId, "LAB-B-" + System.nanoTime());
        UUID modelB = createEquipmentModel(institutionId, "Model-B-" + System.nanoTime(), 50);
        UUID configB = createConfiguration(institutionId, modelB, "Linux");
        assignEquipment(institutionId, labB, configB, 2);

        UUID periodId = createPeriod(institutionId, "PERIOD-DOW-" + System.nanoTime(),
                yesterday.minusDays(5), yesterday.plusDays(5));
        List<ShiftDTO> shifts = replaceShifts(institutionId, periodId, List.of(
                new ReplaceShiftsRequest.ShiftInput(
                        ShiftType.MORNING, LocalTime.of(7, 30), 1, 50, 10,
                        List.of(yesterdayDow, otherDow), true)));
        UUID shiftId = shifts.get(0).id();

        replaceSchedule(institutionId, periodId, labA, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shiftId, otherDow, List.of(1))));
        replaceSchedule(institutionId, periodId, labB, List.of(
                new ReplaceScheduleRequest.ScheduleInput(shiftId, yesterdayDow, List.of(1))));

        cronService.captureYesterday();

        EmissionSnapshot snapshot = fetchSnapshot(institutionId, yesterday).orElseThrow();
        // Only lab B's 2 stations should be counted — lab A's 5 stations must be excluded
        assertEquals(2, snapshot.getStationCount());
        assertTrue(snapshot.getDailyEnergyKwh().compareTo(BigDecimal.ZERO) > 0);
    }
}
