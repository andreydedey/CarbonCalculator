package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.Laboratory;

public interface LaboratoryRepository
        extends JpaRepository<Laboratory, UUID>, JpaSpecificationExecutor<Laboratory>, LaboratoryRepositoryCustom {

    @org.springframework.data.jpa.repository.Query(
            "SELECT CASE WHEN COUNT(le) > 0 THEN true ELSE false END " +
            "FROM LaboratoryEquipment le WHERE le.laboratory.id = :id")
    boolean existsDependentsByLaboratoryId(UUID id);
}
