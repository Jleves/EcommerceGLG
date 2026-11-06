package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.service.PublicOfferPrice;
import java.math.BigDecimal;

public record PublicProductSummaryResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                           BigDecimal precioReferencia, boolean disponible, boolean activo,
                                           boolean destacado, Long subcategoriaId, String primaryThumbnailUrl,
                                           Long selectedVariantId, BigDecimal precioOferta, Integer porcentajeDescuento) {
    public static PublicProductSummaryResponse from(Product product, ProductVariant selected,
                                                    String primaryThumbnailUrl, VariantOffer offer) {
        return new PublicProductSummaryResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), selected.getPrecioReferencia(), selected.isDisponible(),
                product.isActivo(), product.isDestacado(),
                product.getSubcategory() == null ? null : product.getSubcategory().getId(),
                primaryThumbnailUrl, selected.getId(), PublicOfferPrice.price(offer),
                PublicOfferPrice.percent(offer));
    }
}
