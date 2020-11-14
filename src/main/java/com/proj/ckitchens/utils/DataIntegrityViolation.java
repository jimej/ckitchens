package com.proj.ckitchens.utils;

public class DataIntegrityViolation extends RuntimeException {
    public DataIntegrityViolation(String message) {
        super(message);
    }
}
