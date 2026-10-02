package com.ashenox.starter.catalog.characteristic.service;

public class CharacteristicConflictException extends RuntimeException {
    public CharacteristicConflictException() {
        super("Ya existe una característica con ese nombre.");
    }
}
