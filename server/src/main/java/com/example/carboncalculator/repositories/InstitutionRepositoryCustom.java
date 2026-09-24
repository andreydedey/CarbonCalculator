package com.example.carboncalculator.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.Institution;

public interface InstitutionRepositoryCustom {

    Page<InstitutionDTO> findAllWithCounts(Specification<Institution> spec, Pageable pageable);
}
