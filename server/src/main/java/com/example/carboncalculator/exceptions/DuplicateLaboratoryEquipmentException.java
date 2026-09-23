package com.example.carboncalculator.exceptions;

public class DuplicateLaboratoryEquipmentException extends RuntimeException {
    public DuplicateLaboratoryEquipmentException() {
        super("Essa configuração (computador + sistema operacional + monitor) já existe neste laboratório");
    }
}
