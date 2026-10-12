package com.ashenox.starter.catalog.subcategory.dto;

import com.ashenox.starter.catalog.subcategory.model.Subcategory;

public record SubcategoryDetailResponse(Long id, Long categoriaId, String nombre, String descripcion,
                                        long productosAsociados) {
    public static SubcategoryDetailResponse from(Subcategory subcategory, long productosAsociados) {
        return new SubcategoryDetailResponse(subcategory.getId(), subcategory.getCategory().getId(),
                subcategory.getNombre(), subcategory.getDescripcion(), productosAsociados);
    }
}
