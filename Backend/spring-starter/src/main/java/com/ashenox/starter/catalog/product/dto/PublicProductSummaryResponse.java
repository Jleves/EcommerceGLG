package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import java.math.BigDecimal;

public record PublicProductSummaryResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                           BigDecimal precioReferencia, boolean disponible, boolean activo,
                                           boolean destacado, Long subcategoriaId, String primaryThumbnailUrl,
                                           Long selectedVariantId) {
    public static PublicProductSummaryResponse from(Product product, ProductVariant selected,
                                                    String primaryThumbnailUrl) {
        return new PublicProductSummaryResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), selected.getPrecioReferencia(), selected.isDisponible(),
                product.isActivo(), product.isDestacado(),
                product.getSubcategory() == null ? null : product.getSubcategory().getId(),
                primaryThumbnailUrl, selected.getId());
    }
}
