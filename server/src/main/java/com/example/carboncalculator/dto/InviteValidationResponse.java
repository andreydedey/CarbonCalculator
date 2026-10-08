package com.example.carboncalculator.dto;

public record InviteValidationResponse(
        String email,
        String role,
        String institutionName) {}
