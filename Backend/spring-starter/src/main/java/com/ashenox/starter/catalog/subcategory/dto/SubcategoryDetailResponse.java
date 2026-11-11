package com.ashenox.starter.catalog.subcategory.dto;

import com.ashenox.starter.catalog.subcategory.model.Subcategory;

public record SubcategoryDetailResponse(Long id, Long categoriaId, String nombre, String descripcion,
                                        long productosAsociados, long hijosAsociados, Long subcategoriaPadreId) {
    public static SubcategoryDetailResponse from(Subcategory subcategory, long productosAsociados, long hijosAsociados) {
        return new SubcategoryDetailResponse(subcategory.getId(), subcategory.getCategory().getId(),
                subcategory.getNombre(), subcategory.getDescripcion(), productosAsociados, hijosAsociados,
                subcategory.getParentSubcategory() == null ? null : subcategory.getParentSubcategory().getId());
    }
}
