package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String nombre, Long categoriaId, String descripcion,
                              BigDecimal precioReferencia, boolean disponible, boolean activo, boolean destacado) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), product.getPrecioReferencia(), product.isDisponible(),
                product.isActivo(), product.isDestacado());
    }
}
