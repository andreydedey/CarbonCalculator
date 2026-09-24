package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Laboratory;

public final class LaboratoryMapper {

    private LaboratoryMapper() {
    }

    public static LaboratoryDTO toDTO(Laboratory entity) {
        return toDTO(entity, 0, 0);
    }

    public static LaboratoryDTO toDTO(Laboratory entity, int configurationCount, int totalStations) {
        return new LaboratoryDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                configurationCount,
                totalStations,
                entity.getCreatedAt());
    }
}
