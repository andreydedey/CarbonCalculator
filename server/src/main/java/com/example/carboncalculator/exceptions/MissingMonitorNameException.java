package com.example.carboncalculator.exceptions;

public class MissingMonitorNameException extends RuntimeException {
    public MissingMonitorNameException() {
        super("O nome do monitor é obrigatório");
    }
}
