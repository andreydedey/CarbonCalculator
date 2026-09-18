package com.example.carboncalculator.dto;

public record CreateInstitutionRequest(
        String name,
        String acronym,
        String city,
        String state,
        CreateLaboratoryRequest laboratory) {
}
