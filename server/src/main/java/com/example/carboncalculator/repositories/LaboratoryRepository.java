package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.Laboratory;

public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID> {

    List<Laboratory> findByActiveTrue();

    default boolean existsDependentsByLaboratoryId(UUID id) {
        return false;
    }
}
