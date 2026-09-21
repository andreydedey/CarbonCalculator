package com.example.carboncalculator.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileDTO(
        UUID id,
        String name,
        String email,
        boolean admin,
        List<InstitutionMembership> institutions) {

    public record InstitutionMembership(
            UUID institutionId,
            String name,
            String role,
            String status) {}
}
