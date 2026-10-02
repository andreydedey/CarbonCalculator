package com.example.carboncalculator.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.carboncalculator.entities.EmissionSnapshot;

/**
 * Snapshots are institution-scoped via RLS (app.current_institution), so
 * query methods do not need an explicit institution filter.
 */
public interface EmissionSnapshotRepository extends JpaRepository<EmissionSnapshot, UUID> {

    Optional<EmissionSnapshot> findBySnapshotDate(LocalDate snapshotDate);

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    @Query("SELECT s FROM EmissionSnapshot s JOIN FETCH s.academicPeriod ORDER BY s.snapshotDate ASC")
    List<EmissionSnapshot> findAllWithPeriod();

    @Query("SELECT s FROM EmissionSnapshot s JOIN FETCH s.academicPeriod WHERE s.snapshotDate BETWEEN :start AND :end ORDER BY s.snapshotDate ASC")
    List<EmissionSnapshot> findAllWithPeriodBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
