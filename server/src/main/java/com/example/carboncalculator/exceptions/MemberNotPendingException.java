package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class MemberNotPendingException extends RuntimeException {
    public MemberNotPendingException(UUID id) {
        super("Member is not pending: " + id);
    }
}
