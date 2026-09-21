package com.example.carboncalculator.services;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.UserProfileDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;
import com.example.carboncalculator.security.JwtService;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final UserInstitutionRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository,
                       UserInstitutionRepository membershipRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

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
        return toProfileDTO(user, memberships);
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
        return new AuthResponse(accessToken, toProfileDTO(user, memberships));
    }

    private UserProfileDTO toProfileDTO(AppUser user, List<UserInstitution> memberships) {
        List<UserProfileDTO.InstitutionMembership> institutionList = memberships.stream()
                .map(m -> new UserProfileDTO.InstitutionMembership(
                        m.getInstitution().getId(),
                        m.getInstitution().getName(),
                        m.getRole().name(),
                        m.getStatus().name()))
                .toList();

        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isAdmin(),
                institutionList);
    }

    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String email) {
            super("Email already in use: " + email);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid credentials");
        }
    }
}
