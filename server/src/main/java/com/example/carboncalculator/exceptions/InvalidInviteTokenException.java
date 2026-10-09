package com.example.carboncalculator.exceptions;

public class InvalidInviteTokenException extends RuntimeException {

    public InvalidInviteTokenException() {
        super("Convite inválido ou expirado.");
    }
}
