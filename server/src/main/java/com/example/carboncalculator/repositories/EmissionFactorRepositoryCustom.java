package com.example.carboncalculator.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.entities.EmissionFactor;

public interface EmissionFactorRepositoryCustom {

    Page<EmissionFactorDTO> findAllProjected(Specification<EmissionFactor> spec, Pageable pageable);
}
