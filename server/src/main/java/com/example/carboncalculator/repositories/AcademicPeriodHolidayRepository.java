package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.carboncalculator.entities.AcademicPeriodHoliday;

public interface AcademicPeriodHolidayRepository extends JpaRepository<AcademicPeriodHoliday, UUID> {

    List<AcademicPeriodHoliday> findByAcademicPeriodId(UUID academicPeriodId);

    void deleteByAcademicPeriodId(UUID academicPeriodId);
}
