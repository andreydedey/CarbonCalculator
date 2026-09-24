package com.example.carboncalculator.exceptions;

public class MissingEquipmentModelNameException extends RuntimeException {
    public MissingEquipmentModelNameException() {
        super("O nome do modelo de equipamento é obrigatório");
    }
}
