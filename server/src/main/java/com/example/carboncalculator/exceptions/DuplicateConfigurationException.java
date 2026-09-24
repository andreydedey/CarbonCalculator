package com.example.carboncalculator.exceptions;

public class DuplicateConfigurationException extends RuntimeException {
    public DuplicateConfigurationException() {
        super("This configuration already exists");
    }
}
