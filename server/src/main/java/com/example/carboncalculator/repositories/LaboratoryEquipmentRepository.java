package com.example.carboncalculator.repositories;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.carboncalculator.entities.LaboratoryEquipment;

public interface LaboratoryEquipmentRepository extends JpaRepository<LaboratoryEquipment, UUID> {

    List<LaboratoryEquipment> findByLaboratoryId(UUID laboratoryId);

    List<LaboratoryEquipment> findByConfigurationId(UUID configurationId);

    boolean existsByConfigurationId(UUID configurationId);

    boolean existsByLaboratoryIdAndConfigurationId(UUID laboratoryId, UUID configurationId);

    @Query("""
            SELECT le.laboratory.id, COUNT(le), COALESCE(SUM(le.quantity), 0)
            FROM LaboratoryEquipment le
            WHERE le.laboratory.id IN :labIds
            GROUP BY le.laboratory.id
            """)
    List<Object[]> countStationsByLaboratoryIds(@Param("labIds") Collection<UUID> labIds);

    @Query("""
            SELECT COUNT(DISTINCT le.laboratory.id)
            FROM LaboratoryEquipment le
            WHERE le.configuration.id = :configId
            """)
    int countDistinctLaboratoriesByConfigurationId(@Param("configId") UUID configurationId);

    @Query("""
            SELECT COALESCE(SUM(le.quantity), 0)
            FROM LaboratoryEquipment le
            WHERE le.configuration.id = :configId
            """)
    int sumQuantityByConfigurationId(@Param("configId") UUID configurationId);
}
