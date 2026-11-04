package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicResponse;
import com.ashenox.starter.catalog.product.dto.PublicProductDetailResponse;
import com.ashenox.starter.catalog.product.dto.PublicProductPageResponse;
import com.ashenox.starter.catalog.product.dto.PublicProductSummaryResponse;
import com.ashenox.starter.catalog.product.dto.PublicProductVariantResponse;
import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.service.ProductImageUrlService;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductVariantService variantService;
    private final ProductImageRepository images;
    private final ProductCharacteristicRepository characteristics;
    private final ProductImageUrlService urls;

    @Transactional(readOnly = true)
    public PublicProductPageResponse list(int page, int size) {
        return list(page, size, null, null, null, null, List.of());
    }

    @Transactional(readOnly = true)
    public PublicProductPageResponse list(int page, int size, String query, Long categoryId, Long subcategoryId, String brand,
                                          List<String> rawAttributes) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("La página o el tamaño solicitado no es válido.");
        }
        String search = normalized(query, 160, "La búsqueda no es válida.");
        String brandFilter = normalized(brand, 160, "La marca no es válida.");
        if (categoryId != null && categoryId < 1) throw new BusinessException("La categoría no es válida.");
        if (subcategoryId != null && subcategoryId < 1) throw new BusinessException("La subcategoría no es válida.");
        if (rawAttributes.size() > 10) throw new BusinessException("Hay demasiados filtros de atributos.");
        List<PublicProductFilters.Attribute> attributes = rawAttributes.stream().map(this::parseAttribute).toList();
        Page<Product> result = products.findAll(PublicProductFilters.of(search, categoryId, subcategoryId, brandFilter, attributes),
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by("nombre", "id")));
        return toPage(result, page, size);
    }

    @Transactional(readOnly = true)
    public PublicProductPageResponse related(Long id) {
        Product product = products.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        if (variants.findByProductIdAndActivoTrueOrderByIdAsc(id).isEmpty()) {
            throw new ResourceNotFoundException("El producto solicitado no existe.");
        }
        String brand = characteristics.findByProductIdWithCharacteristic(id).stream()
                .filter(pc -> pc.getCharacteristic().getNombre().equalsIgnoreCase("Marca"))
                .map(pc -> pc.getValor().toLowerCase(Locale.ROOT)).findFirst().orElse(null);
        Page<Product> result = products.findRelated(id, product.getCategory().getId(), brand,
                PageRequest.of(0, 4));
        return toPage(result, 0, 4);
    }

    private PublicProductPageResponse toPage(Page<Product> result, int page, int size) {
        List<Long> productIds = result.getContent().stream().map(Product::getId).toList();
        Map<Long, ProductVariant> selectedByProduct = productIds.isEmpty() ? Map.of()
                : variants.findActiveByProductIds(productIds).stream().collect(Collectors.toMap(
                variant -> variant.getProduct().getId(), Function.identity(),
                (left, right) -> preferred().compare(left, right) <= 0 ? left : right));
        Map<Long, ProductImage> primaryByProduct = productIds.isEmpty() ? Map.of()
                : images.findPublishedPrimaryByProductIds(productIds).stream()
                .collect(Collectors.toMap(image -> image.getProduct().getId(), Function.identity()));
        List<PublicProductSummaryResponse> content = result.getContent().stream().map(product -> {
            ProductImage primary = primaryByProduct.get(product.getId());
            return PublicProductSummaryResponse.from(product, selectedByProduct.get(product.getId()),
                    primary == null ? null : urls.toCloudFrontUrl(primary.getThumbnailKey()));
        }).toList();
        return new PublicProductPageResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private String normalized(String value, int max, String error) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > max) throw new BusinessException(error);
        return trimmed;
    }

    private PublicProductFilters.Attribute parseAttribute(String raw) {
        if (raw == null) throw new BusinessException("El filtro de atributo no es válido.");
        int separator = raw.indexOf(':');
        if (separator < 1) throw new BusinessException("El filtro de atributo no es válido.");
        try {
            Long id = Long.parseLong(raw.substring(0, separator));
            String value = normalized(raw.substring(separator + 1), 500, "El filtro de atributo no es válido.");
            if (id < 1) throw new NumberFormatException();
            return new PublicProductFilters.Attribute(id, value);
        } catch (NumberFormatException exception) {
            throw new BusinessException("El filtro de atributo no es válido.");
        }
    }

    @Transactional(readOnly = true)
    public PublicProductDetailResponse getById(Long id) {
        Product product = products.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        List<ProductVariant> active = variants.findByProductIdAndActivoTrueOrderByIdAsc(id);
        if (active.isEmpty()) throw new ResourceNotFoundException("El producto solicitado no existe.");
        ProductVariant selected = active.stream().min(preferred()).orElseThrow();
        return PublicProductDetailResponse.from(product, selected,
                characteristics.findByProductIdWithCharacteristic(id).stream()
                        .map(ProductCharacteristicResponse::from).toList(),
                images.findByProductIdAndStatusOrderByPositionAscIdAsc(id, "PUBLISHED")
                        .stream().map(urls::toResponse).toList(),
                variantService.responses(active).stream().map(PublicProductVariantResponse::from).toList());
    }

    private static Comparator<ProductVariant> preferred() {
        return Comparator.comparing((ProductVariant variant) -> !variant.isDisponible())
                .thenComparing(ProductVariant::getPrecioReferencia)
                .thenComparing(ProductVariant::getId);
    }
}
