package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.LaboratoryResponse;
import com.example.carboncalculator.entities.Laboratory;

public final class LaboratoryMapper {

    private LaboratoryMapper() {
    }

    public static LaboratoryResponse toDTO(Laboratory entity) {
        return new LaboratoryResponse(
                entity.getId(),
                entity.getName(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}
