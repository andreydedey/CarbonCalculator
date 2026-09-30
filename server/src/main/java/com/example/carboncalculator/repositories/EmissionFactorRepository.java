package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.EmissionFactor;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, UUID> {

    boolean existsByYearAndMonth(Short year, Short month);

    boolean existsByYearAndMonthAndIdNot(Short year, Short month, UUID id);

    Page<EmissionFactor> findByYear(Short year, Pageable pageable);
}
