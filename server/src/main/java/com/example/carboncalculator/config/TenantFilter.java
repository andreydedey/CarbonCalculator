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

@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Institution-Id";

    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of("/institutions");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TenantFilter(DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isExcluded(stripContextPath(request))) {
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

    private String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void respondMissingInstitutionHeader(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Header " + TENANT_HEADER + " é obrigatório\"}");
    }

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
