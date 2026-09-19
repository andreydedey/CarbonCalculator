package com.example.carboncalculator.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.dto.LaboratoryResponse;
import com.example.carboncalculator.repositories.LaboratoryRepository;

/**
 * Testes de integração de laboratório (US-002, US-003, US-004, US-005)
 * contra um PostgreSQL real via Testcontainers — indispensável para provar
 * o isolamento por Row-Level Security (ADR-004), que o H2 não implementa.
 *
 * <p>O datasource da aplicação aqui é reconfigurado para um role
 * não-superuser criado dinamicamente no container (ver {@link
 * #createRestrictedApplicationRole()}), pois o PostgreSQL nunca aplica RLS a
 * um superuser, mesmo com {@code FORCE ROW LEVEL SECURITY} — o usuário
 * padrão do Testcontainers é superuser, o que mascararia uma policy de RLS
 * quebrada nos testes de isolamento (AC-008, AC-010). Isso reproduz a
 * suposição ASM-001/ASM-003 do spec.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LaboratoryControllerIntegrationTest {

    private static final String TENANT_HEADER = "X-Institution-Id";
    private static final String APP_ROLE = "carboncalculator_app";
    private static final String APP_PASSWORD = "app_password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        createRestrictedApplicationRole();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
    }

    private static void createRestrictedApplicationRole() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute("GRANT CREATE, USAGE ON SCHEMA public TO " + APP_ROLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao configurar role restrito de teste", e);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoSpyBean
    private LaboratoryRepository laboratoryRepository;

    private UUID createInstitutionAndReturnId(String acronymPrefix) {
        CreateInstitutionRequest request = new CreateInstitutionRequest(
                "Instituição " + acronymPrefix, acronymPrefix + "-" + System.nanoTime(), "Cidade", "PA",
                new CreateLaboratoryRequest("LABCOMP-01"));
        ResponseEntity<InstitutionResponse> response = restTemplate.postForEntity(
                "/api/v1/institutions", request, InstitutionResponse.class);
        InstitutionResponse institution = response.getBody();
        assertNotNull(institution);
        return institution.id();
    }

    private HttpHeaders headersFor(UUID institutionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, institutionId.toString());
        return headers;
    }

    private ResponseEntity<LaboratoryResponse> createLaboratory(UUID institutionId, String name) {
        return restTemplate.postForEntity("/api/v1/laboratories",
                new HttpEntity<>(new CreateLaboratoryRequest(name), headersFor(institutionId)),
                LaboratoryResponse.class);
    }

    private ResponseEntity<LaboratoryResponse[]> listLaboratories(UUID institutionId, Boolean active) {
        String path = active == null ? "/api/v1/laboratories" : "/api/v1/laboratories?active=" + active;
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headersFor(institutionId)),
                LaboratoryResponse[].class);
    }

    // @spec:AC-004 Laboratório criado com nome
    @Test
    void deveCriarLaboratorioInformandoApenasONome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<LaboratoryResponse> response = createLaboratory(institutionId, "LABCOMP-02");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("LABCOMP-02", response.getBody().name());
        assertTrue(response.getBody().active());

        ResponseEntity<LaboratoryResponse[]> list = listLaboratories(institutionId, null);
        assertTrue(Arrays.stream(list.getBody()).anyMatch(lab -> lab.name().equals("LABCOMP-02")));
    }

    // @spec:AC-005 Laboratório sem nome é rejeitado
    @Test
    void deveRecusarCriacaoDeLaboratorioSemNome() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");

        ResponseEntity<Object> response = restTemplate.postForEntity("/api/v1/laboratories",
                new HttpEntity<>(new CreateLaboratoryRequest(""), headersFor(institutionId)), Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
    @Test
    void deveListarApenasLaboratoriosAtivosPorPadrao() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryResponse inactive = createLaboratory(institutionId, "LABCOMP-INATIVO").getBody();
        restTemplate.exchange("/api/v1/laboratories/" + inactive.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryResponse.class);

        ResponseEntity<LaboratoryResponse[]> list = listLaboratories(institutionId, null);

        assertTrue(Arrays.stream(list.getBody()).allMatch(LaboratoryResponse::active));
        assertFalse(Arrays.stream(list.getBody()).anyMatch(lab -> lab.id().equals(inactive.id())));
    }

    // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
    @Test
    void deveIncluirLaboratoriosInativosQuandoSolicitado() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryResponse inactive = createLaboratory(institutionId, "LABCOMP-INATIVO").getBody();
        restTemplate.exchange("/api/v1/laboratories/" + inactive.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryResponse.class);

        ResponseEntity<LaboratoryResponse[]> list = listLaboratories(institutionId, false);

        assertTrue(Arrays.stream(list.getBody())
                .anyMatch(lab -> lab.id().equals(inactive.id()) && !lab.active()));
        assertTrue(Arrays.stream(list.getBody()).anyMatch(LaboratoryResponse::active));
    }

    // @spec:AC-008 Isolamento por RLS entre instituições
    @Test
    void deveIsolarLaboratoriosPorInstituicaoViaRls() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        LaboratoryResponse labA = createLaboratory(institutionA, "LAB-A").getBody();
        LaboratoryResponse labB = createLaboratory(institutionB, "LAB-B").getBody();

        ResponseEntity<LaboratoryResponse[]> listFromA = listLaboratories(institutionA, true);

        assertTrue(Arrays.stream(listFromA.getBody()).anyMatch(lab -> lab.id().equals(labA.id())));
        assertFalse(Arrays.stream(listFromA.getBody()).anyMatch(lab -> lab.id().equals(labB.id())));
    }

    // @spec:AC-009 Requisição sem identificação de instituição é recusada
    @Test
    void deveRecusarRequisicaoSemHeaderXInstitutionId() {
        ResponseEntity<Object> response = restTemplate.getForEntity("/api/v1/laboratories", Object.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // @spec:AC-010 Acesso direto a laboratório de outra instituição é negado
    @Test
    void deveNegarAcessoDiretoALaboratorioDeOutraInstituicaoComo404() {
        UUID institutionA = createInstitutionAndReturnId("INST-A");
        UUID institutionB = createInstitutionAndReturnId("INST-B");
        LaboratoryResponse labA = createLaboratory(institutionA, "LAB-A").getBody();

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/v1/laboratories/" + labA.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionB)), Object.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // @spec:AC-011 Desativação preserva o laboratório
    @Test
    void deveDesativarLaboratorioPreservandoSeusDados() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryResponse created = createLaboratory(institutionId, "LABCOMP-03").getBody();

        ResponseEntity<LaboratoryResponse> deactivateResponse = restTemplate.exchange(
                "/api/v1/laboratories/" + created.id() + "/deactivate", HttpMethod.PATCH,
                new HttpEntity<>(headersFor(institutionId)), LaboratoryResponse.class);

        assertEquals(HttpStatus.OK, deactivateResponse.getStatusCode());
        LaboratoryResponse deactivated = deactivateResponse.getBody();
        assertFalse(deactivated.active());
        assertEquals(created.id(), deactivated.id());
        assertEquals(created.name(), deactivated.name());

        ResponseEntity<LaboratoryResponse[]> list = listLaboratories(institutionId, false);
        assertTrue(Arrays.stream(list.getBody())
                .anyMatch(lab -> lab.id().equals(created.id()) && lab.name().equals("LABCOMP-03") && !lab.active()));
    }

    // @spec:AC-012 Exclusão bloqueada quando há dependentes
    @Test
    void deveBloquearExclusaoQuandoLaboratorioPossuiDependentes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryResponse created = createLaboratory(institutionId, "LABCOMP-COM-DEPENDENTE").getBody();

        // Esta feature não modela equipamentos/medições (ver "Fora de
        // escopo" no spec) — a entidade dependente pertence a um PRD
        // futuro. O restante do fluxo (HTTP, service, RLS, transação) usa o
        // banco real via Testcontainers; só o sinal "possui dependentes" do
        // repositório é simulado aqui.
        when(laboratoryRepository.existsDependentsByLaboratoryId(created.id())).thenReturn(true);

        ResponseEntity<Object> response = restTemplate.exchange("/api/v1/laboratories/" + created.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(institutionId)), Object.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    // @spec:AC-013 Exclusão permitida quando não há dependentes
    @Test
    void devePermitirExclusaoQuandoLaboratorioNaoPossuiDependentes() {
        UUID institutionId = createInstitutionAndReturnId("UFPA");
        LaboratoryResponse created = createLaboratory(institutionId, "LABCOMP-SEM-DEPENDENTE").getBody();

        ResponseEntity<Void> response = restTemplate.exchange("/api/v1/laboratories/" + created.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(institutionId)), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<LaboratoryResponse[]> list = listLaboratories(institutionId, false);
        assertFalse(Arrays.stream(list.getBody()).anyMatch(lab -> lab.id().equals(created.id())));
    }
}
