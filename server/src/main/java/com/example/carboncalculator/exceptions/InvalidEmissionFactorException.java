package com.example.carboncalculator.exceptions;

public class InvalidEmissionFactorException extends RuntimeException {
    public InvalidEmissionFactorException(String message) {
        super(message);
    }
}
