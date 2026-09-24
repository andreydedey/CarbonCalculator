package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class EquipmentModelHasDependentsException extends RuntimeException {
    public EquipmentModelHasDependentsException(UUID id) {
        super("Modelo de equipamento " + id + " está vinculado a laboratórios; desvincule-o antes de excluir");
    }
}
