package com.example.carboncalculator.exceptions;

public class CannotModifySelfException extends RuntimeException {
    public CannotModifySelfException() {
        super("Cannot modify your own membership");
    }
}
