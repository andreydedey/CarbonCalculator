package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class LaboratoryEquipmentNotFoundException extends RuntimeException {
    public LaboratoryEquipmentNotFoundException(UUID id) {
        super("Vínculo de equipamento não encontrado: " + id);
    }
}
