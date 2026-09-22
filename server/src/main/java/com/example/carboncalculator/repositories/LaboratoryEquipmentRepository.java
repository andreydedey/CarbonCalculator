package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.LaboratoryEquipment;

public interface LaboratoryEquipmentRepository extends JpaRepository<LaboratoryEquipment, UUID> {

    List<LaboratoryEquipment> findByLaboratoryId(UUID laboratoryId);

    boolean existsByEquipmentModelId(UUID equipmentModelId);

    boolean existsByLaboratoryIdAndEquipmentModelIdAndOperatingSystem(
            UUID laboratoryId, UUID equipmentModelId, String operatingSystem);
}
