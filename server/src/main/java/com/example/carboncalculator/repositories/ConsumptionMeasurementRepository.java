package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.carboncalculator.entities.ConsumptionMeasurement;
import com.example.carboncalculator.entities.TargetType;

public interface ConsumptionMeasurementRepository extends JpaRepository<ConsumptionMeasurement, UUID>,
        JpaSpecificationExecutor<ConsumptionMeasurement> {

    @Query("""
            SELECT m FROM ConsumptionMeasurement m
            WHERE m.targetType = 'COMPUTER'
              AND m.equipmentModel.id = :modelId
              AND m.operatingSystem.id = :osId
            ORDER BY m.measurementDate DESC
            """)
    List<ConsumptionMeasurement> findComputerMeasurements(
            @Param("modelId") UUID equipmentModelId,
            @Param("osId") UUID operatingSystemId);

    @Query("""
            SELECT m FROM ConsumptionMeasurement m
            WHERE m.targetType = 'MONITOR'
              AND m.monitor.id = :monitorId
            ORDER BY m.measurementDate DESC
            """)
    List<ConsumptionMeasurement> findMonitorMeasurements(
            @Param("monitorId") UUID monitorId);

    @Query("""
            SELECT m FROM ConsumptionMeasurement m
            WHERE m.targetType = 'COMBINED'
              AND m.equipmentModel.id = :modelId
              AND m.operatingSystem.id = :osId
              AND m.monitor.id = :monitorId
            ORDER BY m.measurementDate DESC
            """)
    List<ConsumptionMeasurement> findCombinedMeasurements(
            @Param("modelId") UUID equipmentModelId,
            @Param("osId") UUID operatingSystemId,
            @Param("monitorId") UUID monitorId);

    @Query("""
            SELECT AVG(m.averageWatts) FROM ConsumptionMeasurement m
            WHERE m.targetType = :targetType
              AND (:modelId IS NULL OR m.equipmentModel.id = :modelId)
              AND (:osId IS NULL OR m.operatingSystem.id = :osId)
              AND (:monitorId IS NULL OR m.monitor.id = :monitorId)
              AND m.id != :excludeId
            """)
    Double findAverageWattsForTarget(
            @Param("targetType") TargetType targetType,
            @Param("modelId") UUID equipmentModelId,
            @Param("osId") UUID operatingSystemId,
            @Param("monitorId") UUID monitorId,
            @Param("excludeId") UUID excludeId);

    @Query("""
            SELECT COUNT(m) FROM ConsumptionMeasurement m
            WHERE m.targetType = :targetType
              AND (:modelId IS NULL OR m.equipmentModel.id = :modelId)
              AND (:osId IS NULL OR m.operatingSystem.id = :osId)
              AND (:monitorId IS NULL OR m.monitor.id = :monitorId)
              AND m.id != :excludeId
            """)
    long countForTarget(
            @Param("targetType") TargetType targetType,
            @Param("modelId") UUID equipmentModelId,
            @Param("osId") UUID operatingSystemId,
            @Param("monitorId") UUID monitorId,
            @Param("excludeId") UUID excludeId);
}
