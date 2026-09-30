package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.AcademicPeriodShift;

public interface AcademicPeriodShiftRepository extends JpaRepository<AcademicPeriodShift, UUID> {

    List<AcademicPeriodShift> findByAcademicPeriodId(UUID academicPeriodId);

    void deleteByAcademicPeriodId(UUID academicPeriodId);
}
