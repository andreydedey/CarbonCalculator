package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class MonitorHasDependentsException extends RuntimeException {
    public MonitorHasDependentsException(UUID id) {
        super("Monitor " + id + " está vinculado a configurações de laboratório; desvincule-o antes de excluir");
    }
}
