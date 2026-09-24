package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class ConfigurationHasDependentsException extends RuntimeException {
    public ConfigurationHasDependentsException(UUID id) {
        super("Configuration " + id + " is in use in laboratories; remove it from all labs before deleting");
    }
}
