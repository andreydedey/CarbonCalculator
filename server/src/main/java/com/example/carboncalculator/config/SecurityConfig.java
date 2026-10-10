package com.example.carboncalculator.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.carboncalculator.security.JwtAuthFilter;
import com.example.carboncalculator.security.OAuth2LoginSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final Optional<OAuth2LoginSuccessHandler> oauth2SuccessHandler;
    private final Optional<ClientRegistrationRepository> clientRegistrationRepository;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          Optional<OAuth2LoginSuccessHandler> oauth2SuccessHandler,
                          Optional<ClientRegistrationRepository> clientRegistrationRepository) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout",
                                "/auth/invitations/*/validate", "/auth/invitations/*/accept",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        oauth2SuccessHandler.ifPresent(handler ->
                http.oauth2Login(oauth2 -> {
                    oauth2.successHandler(handler);
                    clientRegistrationRepository.ifPresent(repo -> {
                        DefaultOAuth2AuthorizationRequestResolver resolver =
                                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
                        resolver.setAuthorizationRequestCustomizer(customizer ->
                                customizer.additionalParameters(params -> params.put("prompt", "select_account")));
                        oauth2.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver));
                    });
                }));

        return http.build();
    }

    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_MANAGER
                ROLE_MANAGER > ROLE_RESEARCHER
                """);
    }

    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
