package com.ashenox.starter.catalog.category.service;

public class CategoryConflictException extends RuntimeException {

    public CategoryConflictException() {
        super("Ya existe una categoría con ese nombre.");
    }
}
