package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.Laboratory;

public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID>, JpaSpecificationExecutor<Laboratory> {

    default boolean existsDependentsByLaboratoryId(UUID id) {
        return false;
    }
}
