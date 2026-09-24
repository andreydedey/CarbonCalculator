package com.example.carboncalculator.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.LaboratoryCompositionDTO;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.entities.Configuration;
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

    private static final Logger log = LoggerFactory.getLogger(LaboratoryEquipmentService.class);

    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ConfigurationService configurationService;

    @Transactional
    public LaboratoryEquipmentDTO create(UUID laboratoryId, CreateLaboratoryEquipmentRequest request) {
        validateQuantity(request.quantity());

        Laboratory laboratory = laboratoryRepository.getReferenceById(laboratoryId);
        Configuration configuration = configurationService.getOrThrow(request.configurationId());

        if (laboratoryEquipmentRepository.existsByLaboratoryIdAndConfigurationId(
                laboratoryId, request.configurationId())) {
            throw new DuplicateLaboratoryEquipmentException();
        }

        LaboratoryEquipment equipment = LaboratoryEquipment.builder()
                .laboratory(laboratory)
                .configuration(configuration)
                .quantity(request.quantity())
                .build();

        LaboratoryEquipmentDTO dto = LaboratoryEquipmentMapper.toDTO(laboratoryEquipmentRepository.save(equipment));
        log.info("Configuration linked to laboratory: laboratoryId={}, configurationId={}", laboratoryId, request.configurationId());
        return dto;
    }

    @Transactional
    public LaboratoryEquipmentDTO update(UUID laboratoryId, UUID id, CreateLaboratoryEquipmentRequest request) {
        validateQuantity(request.quantity());

        LaboratoryEquipment equipment = getOrThrow(id);
        equipment.setQuantity(request.quantity());

        log.info("Laboratory equipment updated: id={}", id);
        return LaboratoryEquipmentMapper.toDTO(laboratoryEquipmentRepository.save(equipment));
    }

    public LaboratoryCompositionDTO getComposition(UUID laboratoryId) {
        List<LaboratoryEquipment> items = laboratoryEquipmentRepository.findByLaboratoryId(laboratoryId);

        List<LaboratoryEquipmentDTO> dtos = items.stream()
                .map(LaboratoryEquipmentMapper::toDTO)
                .toList();

        int totalMachines = items.stream().mapToInt(LaboratoryEquipment::getQuantity).sum();
        long configurationsWithoutMonitor = items.stream()
                .filter(le -> le.getConfiguration().getMonitor() == null
                        && !le.getConfiguration().getEquipmentModel().isHasIntegratedScreen())
                .count();

        return new LaboratoryCompositionDTO(dtos, totalMachines, (int) configurationsWithoutMonitor);
    }

    @Transactional
    public void delete(UUID laboratoryId, UUID id) {
        LaboratoryEquipment equipment = getOrThrow(id);
        laboratoryEquipmentRepository.delete(equipment);
        log.info("Configuration removed from laboratory: laboratoryId={}, id={}", laboratoryId, id);
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
