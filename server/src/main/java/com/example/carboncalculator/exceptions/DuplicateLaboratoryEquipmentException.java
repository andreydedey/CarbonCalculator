package com.example.carboncalculator.exceptions;

public class DuplicateLaboratoryEquipmentException extends RuntimeException {
    public DuplicateLaboratoryEquipmentException() {
        super("Esse modelo já está vinculado a este laboratório com o mesmo sistema operacional");
    }
}
