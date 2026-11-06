package com.ashenox.starter.catalog.product.offer.dto;

import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OfferResponse(Long id, Long productoId, String productoNombre, Long varianteId, String sku,
                            BigDecimal precioReferencia, BigDecimal precioOferta, int porcentajeDescuento,
                            boolean productoActivo, boolean varianteActiva, boolean disponible) {
    public static OfferResponse from(VariantOffer offer) {
        ProductVariant variant = offer.getVariant();
        BigDecimal base = variant.getPrecioReferencia();
        int percent = base.subtract(offer.getPrecioOferta()).multiply(BigDecimal.valueOf(100))
                .divide(base, 0, RoundingMode.DOWN).intValueExact();
        return new OfferResponse(offer.getId(), variant.getProduct().getId(), variant.getProduct().getNombre(),
                variant.getId(), variant.getSku(), base, offer.getPrecioOferta(), percent,
                variant.getProduct().isActivo(), variant.isActivo(), variant.isDisponible());
    }
}
