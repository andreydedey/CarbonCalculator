package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class MonitorNotFoundException extends RuntimeException {
    public MonitorNotFoundException(UUID id) {
        super("Monitor não encontrado: " + id);
    }
}
