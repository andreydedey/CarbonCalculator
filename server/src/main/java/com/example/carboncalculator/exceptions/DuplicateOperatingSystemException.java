package com.example.carboncalculator.exceptions;

public class DuplicateOperatingSystemException extends RuntimeException {
    public DuplicateOperatingSystemException() {
        super("Já existe um sistema operacional com esse nome nesta instituição");
    }
}
