package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.service.PublicOfferPrice;
import java.math.BigDecimal;

public record PublicProductVariantResponse(Long id, Long caracteristicaId, String caracteristicaNombre,
                                           String valor, String unidad, String sku,
                                           BigDecimal precioReferencia, boolean disponible,
                                           BigDecimal precioOferta, Integer porcentajeDescuento) {
    public static PublicProductVariantResponse from(ProductVariantResponse variant, VariantOffer offer) {
        return new PublicProductVariantResponse(variant.id(), variant.caracteristicaId(),
                variant.caracteristicaNombre(), variant.valor(), variant.unidad(), variant.sku(),
                variant.precioReferencia(), variant.disponible(), PublicOfferPrice.price(offer),
                PublicOfferPrice.percent(offer));
    }
}
