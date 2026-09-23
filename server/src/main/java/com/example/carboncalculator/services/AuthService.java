package com.example.carboncalculator.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.UserProfileDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.exceptions.EmailAlreadyExistsException;
import com.example.carboncalculator.exceptions.InvalidCredentialsException;
import com.example.carboncalculator.mappers.UserProfileMapper;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;
import com.example.carboncalculator.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final UserInstitutionRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(String name, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        AppUser user = AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        user = userRepository.save(user);
        log.info("User registered: id={}", user.getId());

        activatePendingInvitations(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(String email, String password) {
        AppUser user = userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        log.info("User logged in: id={}", user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new InvalidCredentialsException();
        }

        var userId = jwtService.extractUserId(refreshToken);
        AppUser user = userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(InvalidCredentialsException::new);

        return buildAuthResponse(user);
    }

    public AppUser findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    }

    public UserProfileDTO getProfile(AppUser user) {
        List<UserInstitution> memberships = membershipRepository.findByUserId(user.getId());
        return UserProfileMapper.toProfileDTO(user, memberships);
    }

    private void activatePendingInvitations(AppUser user) {
        List<UserInstitution> pending = membershipRepository
                .findByUserEmailAndStatus(user.getEmail(), MembershipStatus.PENDING);
        for (UserInstitution membership : pending) {
            membership.setUser(user);
            membership.setUserEmail(null);
            membership.setStatus(MembershipStatus.ACTIVE);
            membershipRepository.save(membership);
        }
    }

    private AuthResponse buildAuthResponse(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        List<UserInstitution> memberships = membershipRepository.findByUserId(user.getId());
        return new AuthResponse(accessToken, UserProfileMapper.toProfileDTO(user, memberships));
    }
}
