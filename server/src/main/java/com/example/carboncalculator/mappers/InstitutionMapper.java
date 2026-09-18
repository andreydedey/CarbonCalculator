package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionResponse;
import com.example.carboncalculator.entities.Institution;

public final class InstitutionMapper {

    private InstitutionMapper() {
    }

    public static Institution toEntity(CreateInstitutionRequest request) {
        return Institution.builder()
                .name(request.name())
                .acronym(request.acronym())
                .city(request.city())
                .state(request.state())
                .build();
    }

    public static InstitutionResponse toDTO(Institution entity) {
        return new InstitutionResponse(
                entity.getId(),
                entity.getName(),
                entity.getAcronym(),
                entity.getCity(),
                entity.getState(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}
