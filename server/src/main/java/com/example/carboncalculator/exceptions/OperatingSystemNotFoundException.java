package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class OperatingSystemNotFoundException extends RuntimeException {
    public OperatingSystemNotFoundException(UUID id) {
        super("Sistema operacional não encontrado: " + id);
    }
}
