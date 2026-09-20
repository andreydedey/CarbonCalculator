package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Laboratory;

public final class LaboratoryMapper {

    private LaboratoryMapper() {
    }

    public static LaboratoryDTO toDTO(Laboratory entity) {
        return new LaboratoryDTO(
                entity.getId(),
                entity.getName(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}
