package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import java.math.BigDecimal;
import java.util.List;

public record ProductSummaryResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                     BigDecimal precioReferencia, boolean disponible, boolean activo, boolean destacado,
                                     Long subcategoriaId, Long varianteInicialId, List<ProductVariantResponse> variantes) {
    public static ProductSummaryResponse from(Product product, List<ProductVariantResponse> variantes) {
        ProductVariantResponse initial = variantes.isEmpty() ? null : variantes.getFirst();
        return new ProductSummaryResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), initial == null ? product.getPrecioReferencia() : initial.precioReferencia(),
                initial == null ? product.isDisponible() : initial.disponible(),
                product.isActivo(), product.isDestacado(),
                product.getSubcategory() == null ? null : product.getSubcategory().getId(),
                initial == null ? null : initial.id(), variantes);
    }
}
