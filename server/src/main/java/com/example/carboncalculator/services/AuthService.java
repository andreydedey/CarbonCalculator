package com.example.carboncalculator.services;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.AuthResponse;
import com.example.carboncalculator.dto.AuthResult;
import com.example.carboncalculator.dto.InviteValidationResponse;
import com.example.carboncalculator.dto.UserProfileDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.exceptions.EmailAlreadyExistsException;
import com.example.carboncalculator.exceptions.InvalidCredentialsException;
import com.example.carboncalculator.exceptions.InvalidInviteTokenException;
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
    private final InviteTokenService inviteTokenService;

    @Transactional(readOnly = true)
    public AuthResult login(String email, String password) {
        AppUser user = userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        log.info("User logged in: id={}", user.getId());
        return buildAuthResult(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken)
                || !"refresh".equals(jwtService.extractTokenType(refreshToken))) {
            throw new InvalidCredentialsException();
        }

        var userId = jwtService.extractUserId(refreshToken);
        AppUser user = userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(InvalidCredentialsException::new);

        List<UserInstitution> memberships = membershipRepository.findByUserId(user.getId());
        boolean hasAccess = user.isAdmin()
                || memberships.stream().anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE);
        if (!hasAccess) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(accessToken, UserProfileMapper.toProfileDTO(user, memberships));
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(AppUser user) {
        List<UserInstitution> memberships = membershipRepository.findByUserId(user.getId());
        return UserProfileMapper.toProfileDTO(user, memberships);
    }

    @Transactional(readOnly = true)
    public InviteValidationResponse validateInvite(String rawToken) {
        UserInstitution membership = findValidInvite(rawToken);
        return new InviteValidationResponse(
                membership.getUserEmail(),
                membership.getRole().name(),
                membership.getInstitution().getName());
    }

    @Transactional
    public AuthResult acceptInvite(String rawToken, String name, String password) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        UserInstitution membership = findValidInvite(rawToken);
        String email = membership.getUserEmail();

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        boolean shouldPromote = membership.isPromoteToAdmin();

        AppUser user = AppUser.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        user = userRepository.save(user);
        log.info("User registered via invite: id={}", user.getId());

        // Activate this invite
        membership.setUser(user);
        membership.setUserEmail(null);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setInviteTokenHash(null);
        membership.setInviteExpiresAt(null);
        membership.setPromoteToAdmin(false);
        membershipRepository.save(membership);

        // Activate other pending invites for the same email
        shouldPromote |= activatePendingInvitations(user);

        if (shouldPromote) {
            user.setAdmin(true);
            userRepository.save(user);
            log.info("User promoted to admin via invite: id={}", user.getId());
        }

        return buildAuthResult(user);
    }

    private UserInstitution findValidInvite(String rawToken) {
        String hash = inviteTokenService.hash(rawToken);
        UserInstitution membership = membershipRepository.findByInviteTokenHash(hash)
                .orElseThrow(InvalidInviteTokenException::new);

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new InvalidInviteTokenException();
        }
        if (membership.getInviteExpiresAt() != null
                && membership.getInviteExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidInviteTokenException();
        }
        return membership;
    }

    /**
     * Activates all pending invitations for the given user's email.
     * @return true if any of the activated invitations had promoteToAdmin set
     */
    private boolean activatePendingInvitations(AppUser user) {
        List<UserInstitution> pending = membershipRepository
                .findByUserEmailAndStatus(user.getEmail(), MembershipStatus.PENDING);
        boolean shouldPromote = false;
        for (UserInstitution membership : pending) {
            if (membership.isPromoteToAdmin()) {
                shouldPromote = true;
            }
            membership.setUser(user);
            membership.setUserEmail(null);
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setInviteTokenHash(null);
            membership.setInviteExpiresAt(null);
            membership.setPromoteToAdmin(false);
            membershipRepository.save(membership);
        }
        return shouldPromote;
    }

    private AuthResult buildAuthResult(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        List<UserInstitution> memberships = membershipRepository.findByUserId(user.getId());
        AuthResponse response = new AuthResponse(accessToken, UserProfileMapper.toProfileDTO(user, memberships));
        return new AuthResult(response, refreshToken);
    }
}
