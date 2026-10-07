package com.example.carboncalculator.exceptions;

public class MissingOperatingSystemNameException extends RuntimeException {
    public MissingOperatingSystemNameException() {
        super("Nome do sistema operacional é obrigatório");
    }
}
