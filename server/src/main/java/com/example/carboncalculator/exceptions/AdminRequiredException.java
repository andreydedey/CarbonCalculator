package com.example.carboncalculator.exceptions;

public class AdminRequiredException extends RuntimeException {
    public AdminRequiredException() {
        super("Only an admin can invite another admin");
    }
}
