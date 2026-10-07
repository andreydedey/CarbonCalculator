package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class OperatingSystemHasDependentsException extends RuntimeException {
    public OperatingSystemHasDependentsException(UUID id) {
        super("Sistema operacional " + id + " está vinculado a configurações; desvincule-o antes de excluir");
    }
}
