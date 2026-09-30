package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.carboncalculator.entities.AcademicPeriod;

public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {

    @Query("SELECT ap FROM AcademicPeriod ap ORDER BY ap.startDate DESC")
    Page<AcademicPeriod> findAllOrdered(Pageable pageable);
}
