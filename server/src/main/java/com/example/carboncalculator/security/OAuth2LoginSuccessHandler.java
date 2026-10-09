package com.example.carboncalculator.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnExpression("!'${app.oauth2.google-client-id:}'.isEmpty()")
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AppUserRepository userRepository;
    private final UserInstitutionRepository membershipRepository;
    private final JwtService jwtService;

    @Value("${app.oauth2.frontend-redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        if (name == null || name.isBlank()) {
            name = email.split("@")[0];
        }

        AppUser user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            List<UserInstitution> pendingInvites = membershipRepository
                    .findByUserEmailAndStatus(email, MembershipStatus.PENDING);

            if (pendingInvites.isEmpty()) {
                log.info("OAuth login rejected — no account or invite for email={}", email);
                response.sendRedirect(frontendRedirectUrl.replace("/oauth/callback", "/login")
                        + "?error=no_invite");
                return;
            }

            user = AppUser.builder()
                    .name(name)
                    .email(email)
                    .oauth2Provider("google")
                    .build();
            user = userRepository.save(user);
            log.info("User created via Google OAuth: id={}, email={}", user.getId(), email);

            activatePendingInvitations(user);
        } else {
            if (user.getOauth2Provider() == null) {
                user.setOauth2Provider("google");
                userRepository.save(user);
            }
            log.info("User logged in via Google OAuth: id={}, email={}", user.getId(), email);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/api/v1/auth/refresh")
                .maxAge(jwtService.getRefreshTokenValidityMs() / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        String redirectUrl = frontendRedirectUrl + "?token=" + accessToken;
        response.sendRedirect(redirectUrl);
    }

    private void activatePendingInvitations(AppUser user) {
        List<UserInstitution> pending = membershipRepository
                .findByUserEmailAndStatus(user.getEmail(), MembershipStatus.PENDING);
        for (UserInstitution membership : pending) {
            membership.setUser(user);
            membership.setUserEmail(null);
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setInviteTokenHash(null);
            membership.setInviteExpiresAt(null);
            membershipRepository.save(membership);
        }
    }
}
