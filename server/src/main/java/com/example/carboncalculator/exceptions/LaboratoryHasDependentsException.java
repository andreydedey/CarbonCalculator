package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class LaboratoryHasDependentsException extends RuntimeException {
    public LaboratoryHasDependentsException(UUID id) {
        super("Laboratório " + id + " possui registros dependentes; desative-o em vez de excluir");
    }
}
