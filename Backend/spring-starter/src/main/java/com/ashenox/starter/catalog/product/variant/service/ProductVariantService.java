package com.ashenox.starter.catalog.product.variant.service;

import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.service.OfferService;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.dto.CreateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.UpdateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.repository.VariantCharacteristicRepository;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final VariantCharacteristicRepository attributes;
    private final CharacteristicRepository characteristics;
    private final ProductCharacteristicRepository sharedAttributes;
    private final OfferService offerService;

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> list(Long productId) {
        if (!products.existsById(productId)) throw new ResourceNotFoundException("El producto solicitado no existe.");
        return responses(variants.findByProductIdOrderByIdAsc(productId));
    }

    public List<ProductVariantResponse> responses(List<ProductVariant> values) {
        if (values.isEmpty()) return List.of();
        Map<Long, VariantCharacteristic> byVariant = new HashMap<>();
        attributes.findByVariantIdsWithCharacteristic(values.stream().map(ProductVariant::getId).toList())
                .forEach(value -> byVariant.put(value.getVariantId(), value));
        return values.stream().map(value -> ProductVariantResponse.from(value, byVariant.get(value.getId()))).toList();
    }

    @Transactional
    public ProductVariantResponse create(Long productId, CreateVariantRequest request) {
        Product product = lockProduct(productId);
        List<ProductVariant> existing = variants.findByProductIdOrderByIdAsc(productId);
        if (existing.isEmpty()) throw new IllegalStateException("El producto no tiene variante inicial.");
        List<VariantCharacteristic> existingAttributes = attributes.findByVariantProductId(productId);
        if (existingAttributes.size() != existing.size()) {
            throw new ProductVariantConflictException("Todas las variantes deben tener un valor antes de agregar otra.");
        }
        Long attributeId = existingAttributes.getFirst().getCharacteristic().getId();
        if (request.caracteristicaId() == null || !attributeId.equals(request.caracteristicaId())) {
            throw new ProductVariantConflictException("El atributo variable debe ser el mismo para todas las variantes.");
        }
        validateDuplicate(existingAttributes, null, request.caracteristicaId(), request.valor(), request.unidad());
        return persistNew(product, request.sku(), request.precioReferencia(), request.disponible(),
                request.caracteristicaId(), request.valor(), request.unidad());
    }

    @Transactional
    public ProductVariantResponse createInitial(Product product, CreateVariantRequest request) {
        return persistNew(product, request.sku(), request.precioReferencia(), request.disponible(),
                request.caracteristicaId(), request.valor(), request.unidad());
    }

    @Transactional
    public ProductVariantResponse update(Long productId, Long variantId, UpdateVariantRequest request) {
        lockProduct(productId);
        List<ProductVariant> existing = variants.findByProductIdOrderByIdAsc(productId);
        ProductVariant variant = ownedVariant(existing, variantId);
        List<VariantCharacteristic> existingAttributes = attributes.findByVariantProductId(productId);
        VariantCharacteristic previous = existingAttributes.stream()
                .filter(value -> value.getVariantId().equals(variantId)).findFirst().orElse(null);
        if (existing.size() > 1 && (previous == null || request.caracteristicaId() == null
                || !previous.getCharacteristic().getId().equals(request.caracteristicaId()))) {
            throw new ProductVariantConflictException("No se puede cambiar el atributo variable con varias variantes.");
        }
        validateDuplicate(existingAttributes, variantId, request.caracteristicaId(), request.valor(), request.unidad());
        Characteristic definition = definition(productId, request.caracteristicaId(), request.valor(), request.unidad());
        String cleanedSku = clean(request.sku());
        String normalizedSku = normalize(cleanedSku);
        checkSku(normalizedSku, variantId);
        offerService.validateBasePrice(variantId, request.precioReferencia());
        variant.setSku(cleanedSku);
        variant.setSkuNormalizado(normalizedSku);
        variant.setPrecioReferencia(request.precioReferencia());
        variant.setDisponible(request.disponible());
        variants.saveAndFlush(variant);
        if (previous != null && definition == null) {
            attributes.delete(previous);
            attributes.flush();
        } else if (definition != null) {
            VariantCharacteristic value = previous == null ? new VariantCharacteristic() : previous;
            value.setVariant(variant);
            value.setCharacteristic(definition);
            setValue(value, request.valor(), request.unidad());
            attributes.saveAndFlush(value);
        }
        return ProductVariantResponse.from(variant, definition == null ? null
                : attributes.findById(variantId).orElseThrow());
    }

    @Transactional
    public ProductVariantResponse setActive(Long productId, Long variantId, boolean active) {
        lockProduct(productId);
        List<ProductVariant> existing = variants.findByProductIdOrderByIdAsc(productId);
        ProductVariant variant = ownedVariant(existing, variantId);
        if (!active && variant.isActivo() && existing.stream().filter(ProductVariant::isActivo).count() == 1) {
            throw new ProductVariantConflictException("No se puede desactivar la última variante activa.");
        }
        variant.setActivo(active);
        variants.saveAndFlush(variant);
        return responses(List.of(variant)).getFirst();
    }

    private ProductVariantResponse persistNew(Product product, String sku, BigDecimal price, Boolean available,
                                               Long characteristicId, String value, String unit) {
        Characteristic definition = definition(product.getId(), characteristicId, value, unit);
        String cleanedSku = clean(sku);
        String normalizedSku = normalize(cleanedSku);
        checkSku(normalizedSku, -1L);
        ProductVariant variant = variants.saveAndFlush(ProductVariant.builder().product(product)
                .sku(cleanedSku).skuNormalizado(normalizedSku).precioReferencia(price)
                .disponible(available).activo(true).build());
        VariantCharacteristic attribute = null;
        if (definition != null) {
            attribute = new VariantCharacteristic();
            attribute.setVariant(variant);
            attribute.setCharacteristic(definition);
            setValue(attribute, value, unit);
            attribute = attributes.saveAndFlush(attribute);
        }
        return ProductVariantResponse.from(variant, attribute);
    }

    private Characteristic definition(Long productId, Long id, String value, String unit) {
        if (id == null) {
            if (value != null || unit != null) throw new BusinessException("El valor requiere una característica.");
            return null;
        }
        if (value == null || value.isBlank() || value.length() > 500 || unit != null && unit.length() > 100) {
            throw new BusinessException("El valor de la variante no es válido.");
        }
        if (sharedAttributes.findByProductIdWithCharacteristic(productId).stream()
                .anyMatch(item -> item.getCharacteristic().getId().equals(id))) {
            throw new ProductVariantConflictException("El atributo variable no puede ser compartido por el producto.");
        }
        return characteristics.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La característica solicitada no existe."));
    }

    private void validateDuplicate(Collection<VariantCharacteristic> existing, Long exceptId,
                                   Long characteristicId, String value, String unit) {
        if (characteristicId == null || value == null) return;
        String normalizedValue = normalize(value);
        String normalizedUnit = normalize(unit);
        if (existing.stream().anyMatch(item -> !item.getVariantId().equals(exceptId)
                && item.getCharacteristic().getId().equals(characteristicId)
                && item.getValorNormalizado().equals(normalizedValue)
                && java.util.Objects.equals(item.getUnidadNormalizada(), normalizedUnit))) {
            throw new ProductVariantConflictException("Ya existe una variante con ese valor y unidad.");
        }
    }

    private void setValue(VariantCharacteristic target, String value, String unit) {
        target.setValor(value.strip());
        target.setValorNormalizado(normalize(value));
        target.setUnidad(clean(unit));
        target.setUnidadNormalizada(normalize(unit));
    }

    private void checkSku(String normalized, Long exceptId) {
        if (normalized != null && variants.existsBySkuNormalizadoAndIdNot(normalized, exceptId)) {
            throw new ProductVariantConflictException("El SKU ya está en uso.");
        }
    }

    private Product lockProduct(Long productId) {
        return products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
    }

    private ProductVariant ownedVariant(List<ProductVariant> values, Long id) {
        return values.stream().filter(item -> item.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La variante solicitada no existe."));
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    private static String normalize(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }
}
