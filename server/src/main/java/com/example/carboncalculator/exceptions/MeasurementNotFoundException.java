package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class MeasurementNotFoundException extends RuntimeException {
    public MeasurementNotFoundException(UUID id) {
        super("Medição de consumo não encontrada: " + id);
    }
}
