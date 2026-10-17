package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;
import java.math.BigDecimal;

public record PublicProductSummaryResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                           BigDecimal precioReferencia, boolean disponible, boolean activo,
                                           boolean destacado, Long subcategoriaId, String primaryThumbnailUrl) {
    public static PublicProductSummaryResponse from(Product product, String primaryThumbnailUrl) {
        return new PublicProductSummaryResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), product.getPrecioReferencia(), product.isDisponible(),
                product.isActivo(), product.isDestacado(),
                product.getSubcategory() == null ? null : product.getSubcategory().getId(), primaryThumbnailUrl);
    }
}
