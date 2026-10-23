package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(Long id, String nombre, Long categoriaId, String descripcion,
                              BigDecimal precioReferencia, boolean disponible, boolean activo, boolean destacado,
                              List<ProductCharacteristicResponse> caracteristicas, Long subcategoriaId,
                              Long varianteInicialId, List<ProductVariantResponse> variantes) {

    public static ProductResponse from(Product product, List<ProductCharacteristicResponse> caracteristicas,
                                       List<ProductVariantResponse> variantes) {
        ProductVariantResponse initial = variantes.isEmpty() ? null : variantes.getFirst();
        return new ProductResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), initial == null ? null : initial.precioReferencia(),
                initial != null && initial.disponible(),
                product.isActivo(), product.isDestacado(), caracteristicas,
                product.getSubcategory() == null ? null : product.getSubcategory().getId(),
                initial == null ? null : initial.id(), variantes);
    }
}
