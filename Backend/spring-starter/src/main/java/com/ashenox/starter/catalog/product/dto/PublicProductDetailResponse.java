package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.product.image.dto.ProductImageResponse;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.service.PublicOfferPrice;
import java.math.BigDecimal;
import java.util.List;

public record PublicProductDetailResponse(Long id, String nombre, Long categoriaId, String descripcion,
                                          BigDecimal precioReferencia, boolean disponible, boolean activo,
                                          boolean destacado, List<ProductCharacteristicResponse> caracteristicas,
                                          Long subcategoriaId, List<ProductImageResponse> images,
                                          Long selectedVariantId, List<PublicProductVariantResponse> variantes,
                                          BigDecimal precioOferta, Integer porcentajeDescuento) {
    public static PublicProductDetailResponse from(Product product, ProductVariant selected,
                                                    List<ProductCharacteristicResponse> caracteristicas,
                                                    List<ProductImageResponse> images,
                                                    List<PublicProductVariantResponse> variantes, VariantOffer offer) {
        return new PublicProductDetailResponse(product.getId(), product.getNombre(), product.getCategory().getId(),
                product.getDescripcion(), selected.getPrecioReferencia(), selected.isDisponible(),
                product.isActivo(), product.isDestacado(), caracteristicas,
                product.getSubcategory() == null ? null : product.getSubcategory().getId(),
                images, selected.getId(), variantes, PublicOfferPrice.price(offer), PublicOfferPrice.percent(offer));
    }
}
