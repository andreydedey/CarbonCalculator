package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.LaboratorySchedule;

public interface LaboratoryScheduleRepository extends JpaRepository<LaboratorySchedule, UUID> {

    List<LaboratorySchedule> findByAcademicPeriodIdAndLaboratoryId(UUID academicPeriodId, UUID laboratoryId);

    List<LaboratorySchedule> findByAcademicPeriodId(UUID academicPeriodId);

    void deleteByAcademicPeriodIdAndLaboratoryId(UUID academicPeriodId, UUID laboratoryId);
}
