package com.example.carboncalculator.exceptions;

public class DuplicateInviteException extends RuntimeException {
    public DuplicateInviteException(String email) {
        super("User already invited: " + email);
    }
}
