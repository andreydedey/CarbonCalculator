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
import com.example.carboncalculator.exceptions.CannotModifySelfException;
import com.example.carboncalculator.exceptions.DuplicateInviteException;
import com.example.carboncalculator.exceptions.InstitutionNotFoundException;
import com.example.carboncalculator.exceptions.InvalidRoleException;
import com.example.carboncalculator.exceptions.LastManagerException;
import com.example.carboncalculator.exceptions.MemberNotFoundException;
import com.example.carboncalculator.mappers.UserMemberMapper;
import com.example.carboncalculator.repositories.AppUserRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;
import com.example.carboncalculator.repositories.UserInstitutionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserInstitutionRepository membershipRepository;
    private final AppUserRepository userRepository;
    private final InstitutionRepository institutionRepository;

    public Page<UserMemberDTO> listMembers(Pageable pageable) {
        UUID institutionId = currentInstitutionId();
        return membershipRepository.findByInstitutionId(institutionId, pageable)
                .map(UserMemberMapper::toDTO);
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
        log.info("User invited: email={}, role={}", email, role);

        return UserMemberMapper.toDTO(membership);
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
}
