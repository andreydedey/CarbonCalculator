package com.example.carboncalculator.controllers;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.config.TenantFilter;
import com.example.carboncalculator.dto.CreateLaboratoryRequest;
import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.repositories.UserInstitutionRepository;
import com.example.carboncalculator.services.LaboratoryService;

/**
 * Testa o roteamento HTTP do {@link LaboratoryController}, com o
 * {@link LaboratoryService} mockado. Para provar o AC-009, o
 * {@link TenantFilter} real (T-003) é registrado junto ao MockMvc — a
 * infraestrutura JDBC dele é mockada, igual em {@code TenantFilterTest}.
 */
class LaboratoryControllerTest {

    private final LaboratoryService laboratoryService = mock(LaboratoryService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final UserInstitutionRepository membershipRepository = mock(UserInstitutionRepository.class);
    private final TenantFilter tenantFilter = new TenantFilter(dataSource, transactionManager, membershipRepository);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LaboratoryController(laboratoryService))
            .addFilters(tenantFilter)
            .build();

    // Admin users bypass the membership check in TenantFilter.
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

    // @spec:AC-004 Laboratório criado com nome
    @Test
    void deveCriarLaboratorioInformandoApenasONome() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        LaboratoryDTO response = new LaboratoryDTO(UUID.randomUUID(), "LABCOMP-02", true, OffsetDateTime.now());
        when(laboratoryService.create(any(CreateLaboratoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/laboratories")
                        .header(TenantFilter.TENANT_HEADER, "550e8400-e29b-41d4-a716-446655440000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"LABCOMP-02\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("LABCOMP-02")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    // @spec:AC-009 Requisição sem identificação de instituição é recusada
    @Test
    void deveRecusarRequisicaoDeLaboratorioSemHeaderXInstitutionId() throws Exception {
        mockMvc.perform(get("/laboratories"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(laboratoryService);
    }
}
