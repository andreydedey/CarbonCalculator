package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.dto.CreateLaboratoryEquipmentRequest;
import com.example.carboncalculator.dto.LaboratoryEquipmentDTO;
import com.example.carboncalculator.entities.Configuration;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratoryEquipment;
import com.example.carboncalculator.exceptions.DuplicateLaboratoryEquipmentException;
import com.example.carboncalculator.exceptions.InvalidQuantityException;
import com.example.carboncalculator.repositories.LaboratoryEquipmentRepository;
import com.example.carboncalculator.repositories.LaboratoryRepository;

class LaboratoryEquipmentServiceTest {

    private final LaboratoryEquipmentRepository laboratoryEquipmentRepository = mock(LaboratoryEquipmentRepository.class);
    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final ConfigurationService configurationService = mock(ConfigurationService.class);

    private LaboratoryEquipmentService service;

    @BeforeEach
    void setUp() {
        service = new LaboratoryEquipmentService(
                laboratoryEquipmentRepository, laboratoryRepository, configurationService);
    }

    private Configuration stubConfiguration(UUID configId) {
        Institution institution = Institution.builder().id(UUID.randomUUID()).build();
        EquipmentModel model = EquipmentModel.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .name("Dell OptiPlex")
                .processor("Intel Core i5")
                .tdpWatts(65)
                .coreCount(6)
                .memoryGb(16)
                .hasIntegratedScreen(false)
                .build();
        return Configuration.builder()
                .id(configId)
                .institution(institution)
                .equipmentModel(model)
                .operatingSystem("Linux")
                .monitor(null)
                .build();
    }

    private LaboratoryEquipment stubLaboratoryEquipment(UUID id, UUID labId, UUID configId, int quantity) {
        Configuration config = stubConfiguration(configId);
        Laboratory laboratory = Laboratory.builder().id(labId).name("LABCOMP-01").active(true).build();
        return LaboratoryEquipment.builder()
                .id(id)
                .laboratory(laboratory)
                .configuration(config)
                .quantity(quantity)
                .build();
    }

    // @spec:AC-044
    @Test
    void deveRecusarVinculacaoComQuantidadeZero() {
        UUID labId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        assertThrows(InvalidQuantityException.class,
                () -> service.create(labId, new CreateLaboratoryEquipmentRequest(configId, 0)));

        verify(laboratoryEquipmentRepository, never()).save(any(LaboratoryEquipment.class));
    }

    // @spec:AC-044
    @Test
    void deveRecusarVinculacaoComQuantidadeNegativa() {
        UUID labId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        assertThrows(InvalidQuantityException.class,
                () -> service.create(labId, new CreateLaboratoryEquipmentRequest(configId, -3)));

        verify(laboratoryEquipmentRepository, never()).save(any(LaboratoryEquipment.class));
    }

    // @spec:AC-045
    @Test
    void deveRecusarVinculacaoDuplicadaDaMesmaConfiguracao() {
        UUID labId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        Configuration config = stubConfiguration(configId);

        when(configurationService.getOrThrow(configId)).thenReturn(config);
        when(laboratoryRepository.getReferenceById(labId))
                .thenReturn(Laboratory.builder().id(labId).name("LABCOMP-01").active(true).build());
        when(laboratoryEquipmentRepository.existsByLaboratoryIdAndConfigurationId(labId, configId))
                .thenReturn(true);

        assertThrows(DuplicateLaboratoryEquipmentException.class,
                () -> service.create(labId, new CreateLaboratoryEquipmentRequest(configId, 10)));

        verify(laboratoryEquipmentRepository, never()).save(any(LaboratoryEquipment.class));
    }

    // @spec:AC-049
    @Test
    void deveAtualizarQuantidadeDaConfiguracao() {
        UUID labId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        LaboratoryEquipment existing = stubLaboratoryEquipment(equipmentId, labId, configId, 10);

        when(laboratoryEquipmentRepository.findById(equipmentId)).thenReturn(Optional.of(existing));
        when(laboratoryEquipmentRepository.save(any(LaboratoryEquipment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LaboratoryEquipmentDTO result = service.update(labId, equipmentId,
                new CreateLaboratoryEquipmentRequest(configId, 25));

        assertEquals(25, result.quantity());
        verify(laboratoryEquipmentRepository).save(existing);
    }

    // @spec:AC-049
    @Test
    void deveRecusarAtualizacaoComQuantidadeZero() {
        UUID labId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        assertThrows(InvalidQuantityException.class,
                () -> service.update(labId, equipmentId, new CreateLaboratoryEquipmentRequest(configId, 0)));

        verify(laboratoryEquipmentRepository, never()).save(any(LaboratoryEquipment.class));
    }
}
