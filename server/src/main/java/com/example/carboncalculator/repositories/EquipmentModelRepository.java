package com.example.carboncalculator.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.carboncalculator.entities.EquipmentModel;

public interface EquipmentModelRepository extends JpaRepository<EquipmentModel, UUID>, JpaSpecificationExecutor<EquipmentModel> {
}
