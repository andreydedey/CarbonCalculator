package com.example.carboncalculator.exceptions;

public class InvalidQuantityException extends RuntimeException {
    public InvalidQuantityException() {
        super("A quantidade deve ser maior que zero");
    }
}
