package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class InstitutionNotFoundException extends RuntimeException {
    public InstitutionNotFoundException(UUID id) {
        super("Institution not found: " + id);
    }
}
