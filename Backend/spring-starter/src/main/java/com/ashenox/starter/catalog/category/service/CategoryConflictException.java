package com.ashenox.starter.catalog.category.service;

public class CategoryConflictException extends RuntimeException {

    public CategoryConflictException(String message) { super(message); }

    public CategoryConflictException() {
        super("Ya existe una categoría con ese nombre.");
    }
}
