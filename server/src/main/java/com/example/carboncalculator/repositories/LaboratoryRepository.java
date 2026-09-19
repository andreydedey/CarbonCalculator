package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.Laboratory;

/**
 * Repository de {@link Laboratory}. Não aplica filtro manual por instituição
 * — o isolamento entre tenants é garantido pelo Row-Level Security do
 * PostgreSQL (ADR-004), configurado pelo {@code TenantFilter}.
 */
public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID> {

    List<Laboratory> findByActiveTrue();

    default boolean existsDependentsByLaboratoryId(UUID id) {
        return false;
    }
}
