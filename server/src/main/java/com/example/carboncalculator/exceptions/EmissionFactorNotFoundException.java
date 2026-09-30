package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class EmissionFactorNotFoundException extends RuntimeException {
    public EmissionFactorNotFoundException(UUID id) {
        super("Fator de emissão não encontrado: " + id);
    }
}
