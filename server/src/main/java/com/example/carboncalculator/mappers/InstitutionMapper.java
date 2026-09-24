package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.CreateInstitutionRequest;
import com.example.carboncalculator.dto.InstitutionDTO;
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

    public static InstitutionDTO toDTO(Institution entity) {
        return toDTO(entity, 0, 0);
    }

    public static InstitutionDTO toDTO(Institution entity, long laboratoryCount, long equipmentCount) {
        return new InstitutionDTO(
                entity.getId(),
                entity.getName(),
                entity.getAcronym(),
                entity.getCity(),
                entity.getState(),
                entity.isActive(),
                laboratoryCount,
                equipmentCount,
                entity.getCreatedAt());
    }
}
