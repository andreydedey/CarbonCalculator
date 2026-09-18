package com.example.carboncalculator.config;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter responsável pelo isolamento multi-tenant via Row-Level
 * Security (ADR-004). Lê o header {@code X-Institution-Id}, publica o valor
 * no {@link TenantContext} e o aplica na sessão do PostgreSQL via
 * {@code set_config('app.current_institution', <id>, true)} — o terceiro
 * argumento {@code true} equivale a {@code SET LOCAL}: o valor vale só para a
 * transação aberta por este filter, e é descartado ao final da requisição.
 *
 * <p>A transação é aberta aqui, e não no controller/service, para garantir
 * que a mesma conexão JDBC usada para o {@code set_config} seja reaproveitada
 * pelas queries do restante da requisição (que participam dela via
 * propagação padrão {@code REQUIRED}). Sem isso, o filtro poderia devolver a
 * conexão ao pool antes do controller pegar outra, perdendo o ajuste do RLS.
 *
 * <p>Endpoints de instituição não exigem o header, pois a tabela
 * {@code institution} não tem RLS habilitado.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Institution-Id";

    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of("/api/v1/institutions");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TenantFilter(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String institutionId = request.getHeader(TENANT_HEADER);
        if (institutionId == null || institutionId.isBlank()) {
            respondMissingInstitutionHeader(response);
            return;
        }

        try {
            TenantContext.setInstitutionId(institutionId);
            runInTenantScopedTransaction(institutionId, request, response, filterChain);
        } finally {
            TenantContext.clear();
        }
    }

    private void runInTenantScopedTransaction(String institutionId, HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            transactionTemplate.execute((TransactionCallback<Void>) status -> {
                applyCurrentInstitution(institutionId);
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new FilterChainException(e);
                }
                return null;
            });
        } catch (FilterChainException e) {
            e.rethrow();
        }
    }

    private void applyCurrentInstitution(String institutionId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection
                    .prepareStatement("SELECT set_config('app.current_institution', ?, true)")) {
                statement.setString(1, institutionId);
                statement.execute();
            }
            return null;
        });
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void respondMissingInstitutionHeader(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Header " + TENANT_HEADER + " é obrigatório\"}");
    }

    /**
     * Carrega uma exceção verificada (IOException/ServletException) através
     * de {@link TransactionCallback}, que só permite {@link RuntimeException}.
     */
    private static final class FilterChainException extends RuntimeException {
        FilterChainException(Exception cause) {
            super(cause);
        }

        void rethrow() throws ServletException, IOException {
            Throwable cause = getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof ServletException servletException) {
                throw servletException;
            }
            throw this;
        }
    }
}
