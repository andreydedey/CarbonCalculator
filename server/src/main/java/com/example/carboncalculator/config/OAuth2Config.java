package com.example.carboncalculator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Configuration
@ConditionalOnExpression("!'${app.oauth2.google-client-id:}'.isEmpty()")
public class OAuth2Config {

    @Value("${app.oauth2.google-client-id}")
    private String googleClientId;

    @Value("${app.oauth2.google-client-secret}")
    private String googleClientSecret;

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
