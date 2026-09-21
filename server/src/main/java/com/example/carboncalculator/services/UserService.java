package com.example.carboncalculator.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.InstitutionRole;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

@Service
public class UserService {

    private final UserInstitutionRepository membershipRepository;
    private final AppUserRepository userRepository;
    private final InstitutionRepository institutionRepository;

    public UserService(UserInstitutionRepository membershipRepository,
                       AppUserRepository userRepository,
                       InstitutionRepository institutionRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
    }

    public List<UserMemberDTO> listMembers() {
        UUID institutionId = currentInstitutionId();
        return membershipRepository.findByInstitutionId(institutionId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public UserMemberDTO invite(String email, String roleName, AppUser inviter) {
        UUID institutionId = currentInstitutionId();
        InstitutionRole role = parseRole(roleName);

        AppUser user = userRepository.findByEmail(email).orElse(null);

        boolean alreadyExists = user != null
                ? membershipRepository.existsByUserIdAndInstitutionId(user.getId(), institutionId)
                : membershipRepository.existsByUserEmailAndInstitutionId(email, institutionId);
        if (alreadyExists) {
            throw new DuplicateInviteException(email);
        }

        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));

        UserInstitution membership = UserInstitution.builder()
                .user(user)
                .userEmail(user == null ? email : null)
                .institution(institution)
                .role(role)
                .status(user != null ? MembershipStatus.ACTIVE : MembershipStatus.PENDING)
                .build();
        membership = membershipRepository.save(membership);

        return toDTO(membership);
    }

    @Transactional
    public UserMemberDTO changeRole(UUID membershipId, String roleName, AppUser requester) {
        InstitutionRole role = parseRole(roleName);
        UUID institutionId = currentInstitutionId();

        UserInstitution membership = membershipRepository.findById(membershipId)
                .filter(m -> m.getInstitution().getId().equals(institutionId))
                .orElseThrow(() -> new MemberNotFoundException(membershipId));

        if (isSelf(membership, requester)) {
            throw new CannotModifySelfException();
        }

        membership.setRole(role);
        membership = membershipRepository.save(membership);
        return toDTO(membership);
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

        if (membership.getRole() == InstitutionRole.GESTOR) {
            long activeGestors = membershipRepository.findByInstitutionId(institutionId).stream()
                    .filter(m -> m.getRole() == InstitutionRole.GESTOR)
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                    .count();
            if (activeGestors <= 1) {
                throw new LastGestorException();
            }
        }

        membershipRepository.delete(membership);
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

    private UserMemberDTO toDTO(UserInstitution membership) {
        String name = membership.getUser() != null ? membership.getUser().getName() : null;
        String email = membership.getUser() != null
                ? membership.getUser().getEmail()
                : membership.getUserEmail();
        return new UserMemberDTO(
                membership.getId(),
                name,
                email,
                membership.getRole().name(),
                membership.getStatus().name());
    }

    public static class DuplicateInviteException extends RuntimeException {
        public DuplicateInviteException(String email) {
            super("User already invited: " + email);
        }
    }

    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException(UUID id) {
            super("Member not found: " + id);
        }
    }

    public static class CannotModifySelfException extends RuntimeException {
        public CannotModifySelfException() {
            super("Cannot modify your own membership");
        }
    }

    public static class LastGestorException extends RuntimeException {
        public LastGestorException() {
            super("Cannot remove the last active gestor");
        }
    }

    public static class InvalidRoleException extends RuntimeException {
        public InvalidRoleException(String role) {
            super("Invalid role: " + role);
        }
    }

    public static class InstitutionNotFoundException extends RuntimeException {
        public InstitutionNotFoundException(UUID id) {
            super("Institution not found: " + id);
        }
    }
}
