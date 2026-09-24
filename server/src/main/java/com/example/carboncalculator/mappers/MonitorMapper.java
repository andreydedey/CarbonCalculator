package com.example.carboncalculator.mappers;

import com.example.carboncalculator.dto.MonitorDTO;
import com.example.carboncalculator.entities.Monitor;

public final class MonitorMapper {

    private MonitorMapper() {
    }

    public static MonitorDTO toDTO(Monitor entity) {
        if (entity == null) {
            return null;
        }
        return new MonitorDTO(
                entity.getId(),
                entity.getName(),
                entity.getWatts(),
                entity.getCreatedAt());
    }
}
