package com.example.carboncalculator.dto;

import java.util.UUID;

public record UserMemberDTO(
        UUID id,
        String name,
        String email,
        String role,
        String status) {}
