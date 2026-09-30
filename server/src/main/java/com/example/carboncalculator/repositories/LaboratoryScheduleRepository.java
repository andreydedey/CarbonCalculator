package com.example.carboncalculator.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.carboncalculator.entities.LaboratorySchedule;

public interface LaboratoryScheduleRepository extends JpaRepository<LaboratorySchedule, UUID> {

    List<LaboratorySchedule> findByShiftIdAndLaboratoryId(UUID shiftId, UUID laboratoryId);

    List<LaboratorySchedule> findByShiftId(UUID shiftId);

    @Query("SELECT ls FROM LaboratorySchedule ls WHERE ls.shift.academicPeriod.id = :periodId AND ls.laboratory.id = :labId")
    List<LaboratorySchedule> findByPeriodIdAndLaboratoryId(UUID periodId, UUID labId);

    @Query("SELECT ls FROM LaboratorySchedule ls WHERE ls.shift.academicPeriod.id = :periodId")
    List<LaboratorySchedule> findByPeriodId(UUID periodId);

    void deleteByShiftIdAndLaboratoryId(UUID shiftId, UUID laboratoryId);

    @Query("DELETE FROM LaboratorySchedule ls WHERE ls.shift.academicPeriod.id = :periodId AND ls.laboratory.id = :labId")
    void deleteByPeriodIdAndLaboratoryId(UUID periodId, UUID labId);
}
