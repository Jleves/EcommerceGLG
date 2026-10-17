package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.image.dto.ProductImageResponse;
import com.ashenox.starter.catalog.product.model.Product;
import java.math.BigDecimal;
import java.util.List;

public record PublicProductDetailResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                          BigDecimal precioReferencia, boolean disponible, boolean activo,
                                          boolean destacado, List<ProductCharacteristicResponse> caracteristicas,
                                          Long subcategoriaId, List<ProductImageResponse> images) {
    public static PublicProductDetailResponse from(Product product,
                                                    List<ProductCharacteristicResponse> caracteristicas,
                                                    List<ProductImageResponse> images) {
        return new PublicProductDetailResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), product.getPrecioReferencia(), product.isDisponible(),
                product.isActivo(), product.isDestacado(), caracteristicas,
                product.getSubcategory() == null ? null : product.getSubcategory().getId(), images);
    }
}
