package com.example.carboncalculator.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.LoginRequest;
import com.example.carboncalculator.dto.RegisterRequest;
import com.example.carboncalculator.dto.UserProfileDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.exceptions.EmailAlreadyExistsException;
import com.example.carboncalculator.exceptions.InvalidCredentialsException;
import com.example.carboncalculator.security.JwtService;
import com.example.carboncalculator.services.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request.name(), request.email(), request.password());
        AppUser user = authService.findByEmail(request.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(user).toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        AppUser user = authService.findByEmail(request.email());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(user).toString())
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me(@AuthenticationPrincipal AppUser user) {
        return ResponseEntity.ok(authService.getProfile(user));
    }

    private ResponseCookie buildRefreshCookie(AppUser user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(jwtService.getRefreshTokenValidityMs() / 1000)
                .sameSite("Strict")
                .build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("refresh_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailExists(EmailAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
    }
}
