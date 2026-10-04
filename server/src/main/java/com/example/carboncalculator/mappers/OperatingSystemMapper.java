package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.OperatingSystemDTO;
import com.example.carboncalculator.entities.OperatingSystem;

public final class OperatingSystemMapper {

    private OperatingSystemMapper() {
    }

    public static OperatingSystemDTO toDTO(OperatingSystem entity) {
        if (entity == null) {
            return null;
        }
        return new OperatingSystemDTO(entity.getId(), entity.getName());
    }
}
