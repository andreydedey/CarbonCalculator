package com.example.carboncalculator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

import jakarta.servlet.FilterChain;

class TenantFilterTest {

    private static final String LABORATORIES_PATH = "/laboratories";
    private static final String INSTITUTIONS_PATH = "/institutions";

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement preparedStatement = mock(PreparedStatement.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);

    private final UserInstitutionRepository membershipRepository = mock(UserInstitutionRepository.class);

    private final TenantFilter filter = new TenantFilter(dataSource, transactionManager, membershipRepository);

    // Admin users bypass the membership check, keeping these tests focused on tenant propagation.
    @BeforeEach
    void authenticateAsAdmin() {
        AppUser admin = AppUser.builder().id(UUID.randomUUID()).email("admin@test.com").admin(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    @AfterEach
    void clearLeakedContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void stubJdbcInfrastructure() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    // @spec:AC-009 Requisição sem identificação de instituição é recusada
    @Test
    void deveRecusarRequisicaoDeLaboratorioSemHeaderXInstitutionId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", LABORATORIES_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
        Mockito.verifyNoInteractions(transactionManager);
    }

    // @spec:AC-008 Isolamento por RLS entre instituições
    @Test
    void deveAplicarInstitutionIdDoHeaderNaSessaoDoBancoEPropagarNoContexto() throws Exception {
        stubJdbcInfrastructure();
        String institutionId = "550e8400-e29b-41d4-a716-446655440000";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", LABORATORIES_PATH);
        request.addHeader(TenantFilter.TENANT_HEADER, institutionId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> institutionIdSeenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> institutionIdSeenDuringChain.set(TenantContext.getInstitutionId());

        filter.doFilter(request, response, chain);

        assertEquals(institutionId, institutionIdSeenDuringChain.get());
        verify(preparedStatement).setString(1, institutionId);
        verify(preparedStatement).execute();
        verify(transactionManager).commit(transactionStatus);
        assertNull(TenantContext.getInstitutionId(), "contexto deve ser limpo após a requisição");
    }

    // @spec:AC-010 Acesso direto a laboratório de outra instituição é negado
    @Test
    void deveIsolarOContextoDeInstituicaoEntreRequisicoesDiferentesSemVazamento() throws Exception {
        stubJdbcInfrastructure();

        String institutionA = "11111111-1111-1111-1111-111111111111";
        MockHttpServletRequest requestA = new MockHttpServletRequest("GET", LABORATORIES_PATH);
        requestA.addHeader(TenantFilter.TENANT_HEADER, institutionA);
        AtomicReference<String> seenInRequestA = new AtomicReference<>();
        FilterChain chainA = (req, res) -> seenInRequestA.set(TenantContext.getInstitutionId());
        filter.doFilter(requestA, new MockHttpServletResponse(), chainA);

        assertEquals(institutionA, seenInRequestA.get());
        assertNull(TenantContext.getInstitutionId(), "contexto da instituição A deve ser limpo antes da próxima requisição");

        String institutionB = "22222222-2222-2222-2222-222222222222";
        MockHttpServletRequest requestB = new MockHttpServletRequest("GET", LABORATORIES_PATH);
        requestB.addHeader(TenantFilter.TENANT_HEADER, institutionB);
        AtomicReference<String> seenInRequestB = new AtomicReference<>();
        FilterChain chainB = (req, res) -> seenInRequestB.set(TenantContext.getInstitutionId());
        filter.doFilter(requestB, new MockHttpServletResponse(), chainB);

        assertEquals(institutionB, seenInRequestB.get());
        assertNull(TenantContext.getInstitutionId());
        verify(preparedStatement).setString(1, institutionA);
        verify(preparedStatement).setString(1, institutionB);
    }

    @Test
    void naoDeveExigirHeaderEmEndpointsDeInstituicao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", INSTITUTIONS_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Mockito.verifyNoInteractions(transactionManager);
    }
}
