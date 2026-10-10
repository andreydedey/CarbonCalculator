package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.UserInstitution;

public final class UserMemberMapper {

    private UserMemberMapper() {}

    public static UserMemberDTO toDTO(UserInstitution membership) {
        return toDTO(membership, null);
    }

    public static UserMemberDTO toDTO(UserInstitution membership, String inviteLink) {
        String name = membership.getUser() != null ? membership.getUser().getName() : null;
        String email = membership.getUser() != null
                ? membership.getUser().getEmail()
                : membership.getUserEmail();
        boolean isAdmin = membership.getUser() != null && membership.getUser().isAdmin();
        return new UserMemberDTO(
                membership.getId(),
                name,
                email,
                membership.getRole().name(),
                membership.getStatus().name(),
                isAdmin,
                inviteLink);
    }
}
