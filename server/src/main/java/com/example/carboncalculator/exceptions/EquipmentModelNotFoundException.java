package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class EquipmentModelNotFoundException extends RuntimeException {
    public EquipmentModelNotFoundException(UUID id) {
        super("Modelo de equipamento não encontrado: " + id);
    }
}
