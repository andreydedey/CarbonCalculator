package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.EmissionSnapshot;

public interface EmissionSnapshotRepository extends JpaRepository<EmissionSnapshot, UUID> {

    List<EmissionSnapshot> findByAcademicPeriodIdOrderBySnapshotDate(UUID academicPeriodId);
}
