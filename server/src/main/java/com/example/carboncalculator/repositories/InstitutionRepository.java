package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.Institution;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    boolean existsByAcronym(String acronym);
}
