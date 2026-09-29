package com.ashenox.starter.catalog.category.dto;

import com.ashenox.starter.catalog.category.model.Category;

public record CategoryResponse(Long id, String nombre, String descripcion, String icono, boolean activo) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getNombre(), category.getDescripcion(),
                category.getIcono(), category.isActivo());
    }
}
