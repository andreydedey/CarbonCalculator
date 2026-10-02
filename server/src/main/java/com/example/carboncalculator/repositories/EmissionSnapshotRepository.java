package com.example.carboncalculator.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.EmissionSnapshot;

/**
 * Snapshots are institution-scoped via RLS (app.current_institution), so
 * query methods do not need an explicit institution filter.
 */
public interface EmissionSnapshotRepository
        extends JpaRepository<EmissionSnapshot, UUID>, JpaSpecificationExecutor<EmissionSnapshot> {

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    @EntityGraph(attributePaths = {"academicPeriod"})
    List<EmissionSnapshot> findAll(Specification<EmissionSnapshot> spec, Sort sort);
}
