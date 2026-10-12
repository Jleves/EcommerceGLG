package com.ashenox.starter.catalog.subcategory.dto;

import com.ashenox.starter.catalog.subcategory.model.Subcategory;

public record SubcategoryResponse(Long id, Long categoriaId, String nombre, String descripcion) {
    public static SubcategoryResponse from(Subcategory subcategory) {
        return new SubcategoryResponse(subcategory.getId(), subcategory.getCategory().getId(),
                subcategory.getNombre(), subcategory.getDescripcion());
    }
}
