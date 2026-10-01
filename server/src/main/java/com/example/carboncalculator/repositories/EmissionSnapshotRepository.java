package com.example.carboncalculator.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.EmissionSnapshot;

/**
 * Snapshots are institution-scoped via RLS (app.current_institution), so
 * query methods do not need an explicit institution filter.
 */
public interface EmissionSnapshotRepository extends JpaRepository<EmissionSnapshot, UUID> {

    Optional<EmissionSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<EmissionSnapshot> findAllByOrderBySnapshotDateAsc();

    List<EmissionSnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate startDate, LocalDate endDate);
}
