package com.example.carboncalculator.exceptions;

public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String state) {
        super("UF inválida: '" + state + "'");
    }
}
