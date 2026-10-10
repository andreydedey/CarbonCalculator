package com.example.carboncalculator.services;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.InstitutionRole;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.exceptions.AdminRequiredException;
import com.example.carboncalculator.exceptions.CannotDemoteAdminException;
import com.example.carboncalculator.exceptions.CannotModifySelfException;
import com.example.carboncalculator.exceptions.DuplicateInviteException;
import com.example.carboncalculator.exceptions.InstitutionNotFoundException;
import com.example.carboncalculator.exceptions.InvalidRoleException;
import com.example.carboncalculator.exceptions.LastManagerException;
import com.example.carboncalculator.exceptions.MemberNotFoundException;
import com.example.carboncalculator.exceptions.MemberNotPendingException;
import com.example.carboncalculator.mappers.UserMemberMapper;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

import java.time.OffsetDateTime;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int INVITE_EXPIRATION_DAYS = 7;

    private final UserInstitutionRepository membershipRepository;
    private final AppUserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final InviteTokenService inviteTokenService;

    @Transactional(readOnly = true)
    public Page<UserMemberDTO> listMembers(Pageable pageable) {
        UUID institutionId = currentInstitutionId();
        return membershipRepository.findByInstitutionId(institutionId, pageable)
                .map(UserMemberMapper::toDTO);
    }

    @Transactional
    public UserMemberDTO invite(String email, String roleName, AppUser inviter) {
        UUID institutionId = currentInstitutionId();

        InstitutionRole role = parseRole(roleName);
        boolean isAdminInvite = role == InstitutionRole.ADMIN;

        if (isAdminInvite && !inviter.isAdmin()) {
            throw new AdminRequiredException();
        }

        AppUser user = userRepository.findByEmail(email).orElse(null);

        boolean alreadyExists = user != null
                ? membershipRepository.existsByUserIdAndInstitutionId(user.getId(), institutionId)
                : membershipRepository.existsByUserEmailAndInstitutionId(email, institutionId);
        if (alreadyExists) {
            throw new DuplicateInviteException(email);
        }

        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        // If user already exists and this is an admin invite, promote immediately
        // and use MANAGER as the actual institution role
        if (isAdminInvite && user != null) {
            user.setAdmin(true);
            userRepository.save(user);
            role = InstitutionRole.MANAGER;
        }

        String inviteLink = null;
        boolean isPending = user == null;

        // For pending admin invites, role is still ADMIN (temporary marker);
        // it will be resolved to MANAGER when the invite is accepted.
        var builder = UserInstitution.builder()
                .user(user)
                .userEmail(isPending ? email : null)
                .institution(institution)
                .role(role)
                .status(isPending ? MembershipStatus.PENDING : MembershipStatus.ACTIVE);

        if (isPending) {
            InviteToken token = generateInviteToken();
            builder.inviteTokenHash(token.hash())
                   .inviteExpiresAt(token.expiresAt());
            inviteLink = token.link();
        }

        UserInstitution membership = membershipRepository.save(builder.build());
        log.info("User invited: email={}, role={}, admin={}, pending={}", email, role, isAdminInvite, isPending);

        return UserMemberMapper.toDTO(membership, inviteLink);
    }

    @Transactional
    public UserMemberDTO resendInvite(UUID membershipId) {
        UUID institutionId = currentInstitutionId();

        UserInstitution membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new MemberNotFoundException(membershipId));

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new MemberNotPendingException(membershipId);
        }

        InviteToken token = generateInviteToken();
        membership.setInviteTokenHash(token.hash());
        membership.setInviteExpiresAt(token.expiresAt());
        membership = membershipRepository.save(membership);

        log.info("Invite resent: membershipId={}", membershipId);
        return UserMemberMapper.toDTO(membership, token.link());
    }

    @Transactional
    public UserMemberDTO changeRole(UUID membershipId, String roleName, AppUser requester) {
        InstitutionRole role = parseRole(roleName);
        boolean isAdminPromotion = role == InstitutionRole.ADMIN;

        if (isAdminPromotion && !requester.isAdmin()) {
            throw new AdminRequiredException();
        }

        UUID institutionId = currentInstitutionId();

        UserInstitution membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new MemberNotFoundException(membershipId));

        if (isSelf(membership, requester)) {
            throw new CannotModifySelfException();
        }

        AppUser target = membership.getUser();
        if (!isAdminPromotion && target != null && target.isAdmin()) {
            throw new CannotDemoteAdminException();
        }

        if (isAdminPromotion) {
            // Promote user to global admin; keep MANAGER as institution role
            if (target != null && !target.isAdmin()) {
                target.setAdmin(true);
                userRepository.save(target);
                log.info("User promoted to admin: userId={}", target.getId());
            }
            role = InstitutionRole.MANAGER;
        }

        membership.setRole(role);
        membership = membershipRepository.save(membership);
        log.info("Member role changed: membershipId={}, newRole={}", membershipId, role);
        return UserMemberMapper.toDTO(membership);
    }

    @Transactional
    public void revoke(UUID membershipId, AppUser requester) {
        UUID institutionId = currentInstitutionId();

        UserInstitution membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new MemberNotFoundException(membershipId));

        if (isSelf(membership, requester)) {
            throw new CannotModifySelfException();
        }

        if (membership.getRole() == InstitutionRole.MANAGER) {
            long activeManagers = membershipRepository.findByInstitutionId(institutionId).stream()
                    .filter(m -> m.getRole() == InstitutionRole.MANAGER)
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                    .count();
            if (activeManagers <= 1) {
                throw new LastManagerException();
            }
        }

        membershipRepository.delete(membership);
        log.info("Member revoked: membershipId={}", membershipId);
    }

    private boolean isSelf(UserInstitution membership, AppUser requester) {
        return membership.getUser() != null
                && membership.getUser().getId().equals(requester.getId());
    }

    private UUID currentInstitutionId() {
        return UUID.fromString(TenantContext.getInstitutionId());
    }

    private InstitutionRole parseRole(String roleName) {
        try {
            return InstitutionRole.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRoleException(roleName);
        }
    }

    private record InviteToken(String hash, OffsetDateTime expiresAt, String link) {}

    private InviteToken generateInviteToken() {
        String rawToken = inviteTokenService.generateToken();
        return new InviteToken(
                inviteTokenService.hash(rawToken),
                OffsetDateTime.now().plusDays(INVITE_EXPIRATION_DAYS),
                "/register?token=" + rawToken);
    }
}
