package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import java.math.BigDecimal;

public record PublicProductVariantResponse(Long id, Long caracteristicaId, String caracteristicaNombre,
                                           String valor, String unidad, String sku,
                                           BigDecimal precioReferencia, boolean disponible) {
    public static PublicProductVariantResponse from(ProductVariantResponse variant) {
        return new PublicProductVariantResponse(variant.id(), variant.caracteristicaId(),
                variant.caracteristicaNombre(), variant.valor(), variant.unidad(), variant.sku(),
                variant.precioReferencia(), variant.disponible());
    }
}
