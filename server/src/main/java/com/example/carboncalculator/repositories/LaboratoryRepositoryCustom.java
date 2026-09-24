package com.example.carboncalculator.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.dto.LaboratoryDTO;
import com.example.carboncalculator.entities.Laboratory;

public interface LaboratoryRepositoryCustom {

    Page<LaboratoryDTO> findAllWithCounts(Specification<Laboratory> spec, Pageable pageable);
}
