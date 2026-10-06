package com.example.carboncalculator.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.carboncalculator.entities.ClassOccurrence;

public interface ClassOccurrenceRepository extends JpaRepository<ClassOccurrence, UUID> {

    @Query("SELECT o FROM ClassOccurrence o WHERE o.shift.academicPeriod.id = :periodId")
    List<ClassOccurrence> findByPeriodId(UUID periodId);

    @Query("""
            SELECT o FROM ClassOccurrence o
            WHERE o.shift.academicPeriod.id = :periodId
              AND o.date BETWEEN :from AND :to
            """)
    List<ClassOccurrence> findByPeriodIdAndDateBetween(UUID periodId, LocalDate from, LocalDate to);

    Optional<ClassOccurrence> findByShiftIdAndLaboratoryIdAndDateAndSlot(
            UUID shiftId, UUID laboratoryId, LocalDate date, short slot);
}
