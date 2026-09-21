package com.example.carboncalculator.exceptions;

public class LastManagerException extends RuntimeException {
    public LastManagerException() {
        super("Cannot remove the last active manager");
    }
}
