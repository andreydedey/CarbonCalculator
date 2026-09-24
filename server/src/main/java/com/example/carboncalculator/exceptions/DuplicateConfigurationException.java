package com.example.carboncalculator.exceptions;

public class DuplicateConfigurationException extends RuntimeException {
    public DuplicateConfigurationException() {
        super("Esta configuração já existe na instituição.");
    }
}
