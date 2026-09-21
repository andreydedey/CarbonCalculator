package com.example.carboncalculator.exceptions;

public class MissingLaboratoryNameException extends RuntimeException {
    public MissingLaboratoryNameException() {
        super("O nome do laboratório é obrigatório");
    }
}
