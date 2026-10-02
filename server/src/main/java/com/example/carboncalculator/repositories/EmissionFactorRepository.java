package com.example.carboncalculator.repositories;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.EmissionFactor;

public interface EmissionFactorRepository
        extends JpaRepository<EmissionFactor, UUID>, JpaSpecificationExecutor<EmissionFactor>,
        EmissionFactorRepositoryCustom {

    boolean existsByReferenceMonth(YearMonth referenceMonth);

    boolean existsByReferenceMonthAndIdNot(YearMonth referenceMonth, UUID id);

    Optional<EmissionFactor> findByReferenceMonth(YearMonth referenceMonth);
}
