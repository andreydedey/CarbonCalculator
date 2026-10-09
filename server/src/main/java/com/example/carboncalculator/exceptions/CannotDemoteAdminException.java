package com.example.carboncalculator.exceptions;

public class CannotDemoteAdminException extends RuntimeException {
    public CannotDemoteAdminException() {
        super("Cannot change the role of a global admin");
    }
}
