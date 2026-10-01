package com.example.carboncalculator.repositories;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.EmissionFactor;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, UUID> {

    boolean existsByReferenceMonth(YearMonth referenceMonth);

    boolean existsByReferenceMonthAndIdNot(YearMonth referenceMonth, UUID id);

    Page<EmissionFactor> findByReferenceMonthBetween(YearMonth start, YearMonth end, Pageable pageable);
}
