package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.carboncalculator.entities.Configuration;

public interface ConfigurationRepository extends JpaRepository<Configuration, UUID>,
        JpaSpecificationExecutor<Configuration> {

    @Query("""
            SELECT COUNT(c) > 0 FROM Configuration c
            WHERE c.institution.id = :instId
              AND c.equipmentModel.id = :modelId
              AND c.operatingSystem = :os
              AND (c.monitor.id = :monitorId OR (c.monitor IS NULL AND :monitorId IS NULL))
            """)
    boolean existsDuplicate(
            @Param("instId") UUID institutionId,
            @Param("modelId") UUID equipmentModelId,
            @Param("os") String operatingSystem,
            @Param("monitorId") UUID monitorId);

    @Query("""
            SELECT COUNT(c) > 0 FROM Configuration c
            WHERE c.institution.id = :instId
              AND c.equipmentModel.id = :modelId
              AND c.operatingSystem = :os
              AND (c.monitor.id = :monitorId OR (c.monitor IS NULL AND :monitorId IS NULL))
              AND c.id != :excludeId
            """)
    boolean existsDuplicateExcluding(
            @Param("instId") UUID institutionId,
            @Param("modelId") UUID equipmentModelId,
            @Param("os") String operatingSystem,
            @Param("monitorId") UUID monitorId,
            @Param("excludeId") UUID excludeId);

    boolean existsByEquipmentModelId(UUID equipmentModelId);

    boolean existsByMonitorId(UUID monitorId);
}
