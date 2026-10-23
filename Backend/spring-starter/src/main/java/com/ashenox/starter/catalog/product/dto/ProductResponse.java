package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(Long id, String nombre, Long categoriaId, String descripcion,
                              BigDecimal precioReferencia, boolean disponible, boolean activo, boolean destacado,
                              List<ProductCharacteristicResponse> caracteristicas, Long subcategoriaId,
                              Long varianteInicialId) {

    public static ProductResponse from(Product product, List<ProductCharacteristicResponse> caracteristicas,
                                       Long varianteInicialId) {
        return new ProductResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), product.getPrecioReferencia(), product.isDisponible(),
                product.isActivo(), product.isDestacado(), caracteristicas,
                product.getSubcategory() == null ? null : product.getSubcategory().getId(), varianteInicialId);
    }
}
