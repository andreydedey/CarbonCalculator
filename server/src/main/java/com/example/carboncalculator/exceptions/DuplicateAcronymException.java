package com.example.carboncalculator.exceptions;

public class DuplicateAcronymException extends RuntimeException {
    public DuplicateAcronymException(String acronym) {
        super("Já existe uma instituição com a sigla '" + acronym + "'");
    }
}
