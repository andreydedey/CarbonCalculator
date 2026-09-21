package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.UserMemberDTO;
import com.example.carboncalculator.entities.UserInstitution;

public final class UserMemberMapper {

    private UserMemberMapper() {}

    public static UserMemberDTO toDTO(UserInstitution membership) {
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
}
