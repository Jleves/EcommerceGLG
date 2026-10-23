package com.ashenox.starter.catalog.product.variant.dto;

import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import java.math.BigDecimal;

public record ProductVariantResponse(Long id, String sku, BigDecimal precioReferencia, boolean disponible,
                                     boolean activo, Long caracteristicaId, String caracteristicaNombre,
                                     String valor, String unidad) {
    public static ProductVariantResponse from(ProductVariant variant, VariantCharacteristic attribute) {
        return new ProductVariantResponse(variant.getId(), variant.getSku(), variant.getPrecioReferencia(),
                variant.isDisponible(), variant.isActivo(),
                attribute == null ? null : attribute.getCharacteristic().getId(),
                attribute == null ? null : attribute.getCharacteristic().getNombre(),
                attribute == null ? null : attribute.getValor(),
                attribute == null ? null : attribute.getUnidad());
    }
}
