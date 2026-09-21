package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(UUID id) {
        super("Member not found: " + id);
    }
}
