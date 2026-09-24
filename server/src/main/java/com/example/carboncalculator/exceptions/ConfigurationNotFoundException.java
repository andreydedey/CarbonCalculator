package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class ConfigurationNotFoundException extends RuntimeException {
    public ConfigurationNotFoundException(UUID id) {
        super("Configuration " + id + " not found");
    }
}
