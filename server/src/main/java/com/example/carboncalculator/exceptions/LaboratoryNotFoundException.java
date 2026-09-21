package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class LaboratoryNotFoundException extends RuntimeException {
    public LaboratoryNotFoundException(UUID id) {
        super("Laboratório não encontrado: " + id);
    }
}
