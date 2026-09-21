package com.example.carboncalculator.mappers;

import java.util.List;

import com.example.carboncalculator.dto.UserProfileDTO;
import com.example.carboncalculator.entities.AppUser;
import com.example.carboncalculator.entities.UserInstitution;

public final class UserProfileMapper {

    private UserProfileMapper() {}

    public static UserProfileDTO toProfileDTO(AppUser user, List<UserInstitution> memberships) {
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
}
