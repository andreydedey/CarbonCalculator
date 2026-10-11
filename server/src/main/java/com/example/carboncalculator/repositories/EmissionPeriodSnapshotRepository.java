package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.EmissionPeriodSnapshot;

public interface EmissionPeriodSnapshotRepository extends JpaRepository<EmissionPeriodSnapshot, UUID> {
}
