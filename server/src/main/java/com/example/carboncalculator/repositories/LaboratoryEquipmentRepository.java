package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.carboncalculator.entities.LaboratoryEquipment;

public interface LaboratoryEquipmentRepository extends JpaRepository<LaboratoryEquipment, UUID> {

    List<LaboratoryEquipment> findByLaboratoryId(UUID laboratoryId);

    boolean existsByEquipmentModelId(UUID equipmentModelId);

    boolean existsByMonitorId(UUID monitorId);

    @Query("""
            SELECT COUNT(le) > 0 FROM LaboratoryEquipment le
            WHERE le.laboratory.id = :labId
              AND le.equipmentModel.id = :modelId
              AND le.operatingSystem = :os
              AND (le.monitor.id = :monitorId OR (le.monitor IS NULL AND :monitorId IS NULL))
            """)
    boolean existsDuplicate(
            @Param("labId") UUID laboratoryId,
            @Param("modelId") UUID equipmentModelId,
            @Param("os") String operatingSystem,
            @Param("monitorId") UUID monitorId);
}
