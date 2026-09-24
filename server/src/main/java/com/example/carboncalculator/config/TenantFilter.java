package com.example.carboncalculator.config;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    public static final String TENANT_HEADER = "X-Institution-Id";

    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of("/institutions", "/auth");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserInstitutionRepository membershipRepository;

    public TenantFilter(DataSource dataSource, PlatformTransactionManager transactionManager,
                        UserInstitutionRepository membershipRepository) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.membershipRepository = membershipRepository;
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
            log.warn("Missing {} header on {} {}", TENANT_HEADER, request.getMethod(), stripContextPath(request));
            respondMissingInstitutionHeader(response);
            return;
        }

        if (!hasAccess(institutionId)) {
            log.warn("Access denied to institution {} on {} {}", institutionId, request.getMethod(), stripContextPath(request));
            respondForbidden(response);
            return;
        }

        MDC.put("institutionId", institutionId);
        try {
            TenantContext.setInstitutionId(institutionId);
            addInstitutionRolesToAuth(institutionId);
            runInTenantScopedTransaction(institutionId, request, response, filterChain);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean hasAccess(String institutionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUser user)) {
            return false;
        }

        if (user.isAdmin()) {
            return true;
        }

        UUID instId;
        try {
            instId = UUID.fromString(institutionId);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return membershipRepository.findByUserIdAndInstitutionId(user.getId(), instId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
    }

    private void addInstitutionRolesToAuth(String institutionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUser user)) {
            return;
        }

        UUID instId;
        try {
            instId = UUID.fromString(institutionId);
        } catch (IllegalArgumentException e) {
            return;
        }

        membershipRepository.findByUserIdAndInstitutionId(user.getId(), instId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .ifPresent(membership -> {
                    List<org.springframework.security.core.GrantedAuthority> authorities =
                            new java.util.ArrayList<>(auth.getAuthorities());
                    authorities.add(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                    "ROLE_" + membership.getRole().name()));

                    var newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(newAuth);
                });
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

    private void respondForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Acesso negado a esta instituição\"}");
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
