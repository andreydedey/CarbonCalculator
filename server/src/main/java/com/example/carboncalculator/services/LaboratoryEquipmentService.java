package com.example.carboncalculator.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.LaboratoryCompositionDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratoryEquipment;
import com.example.carboncalculator.exceptions.DuplicateLaboratoryEquipmentException;
import com.example.carboncalculator.exceptions.InvalidQuantityException;
import com.example.carboncalculator.exceptions.LaboratoryEquipmentNotFoundException;
import com.example.carboncalculator.mappers.LaboratoryEquipmentMapper;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LaboratoryEquipmentService {

    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EquipmentModelService equipmentModelService;

    @Transactional
    public LaboratoryEquipmentDTO create(UUID laboratoryId, CreateLaboratoryEquipmentRequest request) {
        validateQuantity(request.quantity());

        Laboratory laboratory = laboratoryRepository.getReferenceById(laboratoryId);
        EquipmentModel model = equipmentModelService.getOrThrow(request.equipmentModelId());

        if (laboratoryEquipmentRepository.existsByLaboratoryIdAndEquipmentModelIdAndOperatingSystem(
                laboratoryId, request.equipmentModelId(), request.operatingSystem())) {
            throw new DuplicateLaboratoryEquipmentException();
        }

        LaboratoryEquipment equipment = LaboratoryEquipment.builder()
                .laboratory(laboratory)
                .equipmentModel(model)
                .operatingSystem(request.operatingSystem())
                .quantity(request.quantity())
                .build();

        return LaboratoryEquipmentMapper.toDTO(laboratoryEquipmentRepository.save(equipment));
    }

    @Transactional
    public LaboratoryEquipmentDTO update(UUID laboratoryId, UUID id, CreateLaboratoryEquipmentRequest request) {
        validateQuantity(request.quantity());

        LaboratoryEquipment equipment = getOrThrow(id);
        equipment.setOperatingSystem(request.operatingSystem());
        equipment.setQuantity(request.quantity());

        return LaboratoryEquipmentMapper.toDTO(laboratoryEquipmentRepository.save(equipment));
    }

    public LaboratoryCompositionDTO getComposition(UUID laboratoryId) {
        List<LaboratoryEquipment> items = laboratoryEquipmentRepository.findByLaboratoryId(laboratoryId);

        List<LaboratoryEquipmentDTO> dtos = items.stream()
                .map(LaboratoryEquipmentMapper::toDTO)
                .toList();

        int totalMachines = items.stream().mapToInt(LaboratoryEquipment::getQuantity).sum();
        long modelsWithoutMonitor = items.stream()
                .map(LaboratoryEquipment::getEquipmentModel)
                .distinct()
                .filter(m -> !m.hasMonitor())
                .count();

        return new LaboratoryCompositionDTO(dtos, totalMachines, (int) modelsWithoutMonitor);
    }

    @Transactional
    public void delete(UUID laboratoryId, UUID id) {
        LaboratoryEquipment equipment = getOrThrow(id);
        laboratoryEquipmentRepository.delete(equipment);
    }

    private LaboratoryEquipment getOrThrow(UUID id) {
        return laboratoryEquipmentRepository.findById(id)
                .orElseThrow(() -> new LaboratoryEquipmentNotFoundException(id));
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidQuantityException();
        }
    }
}
