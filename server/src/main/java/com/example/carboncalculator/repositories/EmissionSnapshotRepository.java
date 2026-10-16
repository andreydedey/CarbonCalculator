package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.EmissionSnapshot;

public interface EmissionSnapshotRepository
        extends JpaRepository<EmissionSnapshot, UUID>, JpaSpecificationExecutor<EmissionSnapshot> {
}
