package com.example.carboncalculator.dto;

public record UpdateInstitutionRequest(
        String name,
        String acronym,
        String city,
        String state) {
}
