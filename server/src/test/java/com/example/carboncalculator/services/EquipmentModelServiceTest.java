package com.example.carboncalculator.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.carboncalculator.config.TenantContext;
import com.example.carboncalculator.dto.CreateEquipmentModelRequest;
import com.example.carboncalculator.dto.EquipmentModelDTO;
import com.example.carboncalculator.entities.EquipmentModel;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.exceptions.EquipmentModelHasDependentsException;
import com.example.carboncalculator.exceptions.GpuTdpRequiredException;
import com.example.carboncalculator.exceptions.MissingEquipmentModelNameException;
import com.example.carboncalculator.repositories.ConfigurationRepository;
import com.example.carboncalculator.repositories.EquipmentModelRepository;
import com.example.carboncalculator.repositories.InstitutionRepository;

class EquipmentModelServiceTest {

    private final EquipmentModelRepository equipmentModelRepository = mock(EquipmentModelRepository.class);
    private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);

    private EquipmentModelService service;

    private static final UUID INSTITUTION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        service = new EquipmentModelService(equipmentModelRepository, configurationRepository, institutionRepository);
        TenantContext.setInstitutionId(INSTITUTION_ID.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EquipmentModel savedModel(String name) {
        Institution institution = Institution.builder().id(INSTITUTION_ID).build();
        return EquipmentModel.builder()
                .id(UUID.randomUUID())
                .institution(institution)
                .name(name)
                .processor("Intel Core i5")
                .tdpWatts(65)
                .coreCount(6)
                .memoryGb(16)
                .hasIntegratedScreen(false)
                .build();
    }

    // @spec:AC-034
    @Test
    void deveCriarModeloDeEquipamentoComDadosValidos() {
        Institution institution = Institution.builder().id(INSTITUTION_ID).build();
        when(institutionRepository.getReferenceById(INSTITUTION_ID)).thenReturn(institution);
        when(equipmentModelRepository.save(any(EquipmentModel.class))).thenAnswer(inv -> {
            EquipmentModel m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                "Dell OptiPlex 3080", "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);
        EquipmentModelDTO dto = service.create(request);

        assertNotNull(dto.id());
        assertEquals("Dell OptiPlex 3080", dto.name());
        assertEquals(16, dto.memoryGb());
        verify(equipmentModelRepository).save(any(EquipmentModel.class));
    }

    // @spec:AC-035
    @Test
    void deveRecusarCriacaoDeModeloSemNome() {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                "", "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);

        assertThrows(MissingEquipmentModelNameException.class, () -> service.create(request));

        verify(equipmentModelRepository, never()).save(any(EquipmentModel.class));
    }

    // @spec:AC-035
    @Test
    void deveRecusarCriacaoDeModeloComNomeNulo() {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                null, "desktop", "Intel Core i5", 65, 6, 16, null, null, false, null);

        assertThrows(MissingEquipmentModelNameException.class, () -> service.create(request));
    }

    @Test
    void deveRecusarCriacaoDeModeloComGpuSemTdp() {
        CreateEquipmentModelRequest request = new CreateEquipmentModelRequest(
                "Gaming PC", "desktop", "Intel Core i9", 125, 12, 32, "NVIDIA RTX 4080", null, false, null);

        assertThrows(GpuTdpRequiredException.class, () -> service.create(request));

        verify(equipmentModelRepository, never()).save(any(EquipmentModel.class));
    }

    // @spec:AC-041
    @Test
    void devePermitirExclusaoDeModeloSemConfiguracoes() {
        UUID modelId = UUID.randomUUID();
        EquipmentModel model = savedModel("Model to Delete");
        model.setId(modelId);

        when(equipmentModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(configurationRepository.existsByEquipmentModelId(modelId)).thenReturn(false);

        service.delete(modelId);

        verify(equipmentModelRepository).delete(model);
    }

    // @spec:AC-042
    @Test
    void deveBloquearExclusaoDeModeloVinculadoAConfiguracao() {
        UUID modelId = UUID.randomUUID();
        EquipmentModel model = savedModel("Model in Use");
        model.setId(modelId);

        when(equipmentModelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(configurationRepository.existsByEquipmentModelId(modelId)).thenReturn(true);

        assertThrows(EquipmentModelHasDependentsException.class, () -> service.delete(modelId));

        verify(equipmentModelRepository, never()).delete(any(EquipmentModel.class));
    }
}
