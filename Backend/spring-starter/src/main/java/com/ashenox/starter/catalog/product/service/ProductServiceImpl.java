package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristicId;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicResponse;
import com.ashenox.starter.catalog.product.dto.ProductResponse;
import com.ashenox.starter.catalog.product.dto.ProductPageResponse;
import com.ashenox.starter.catalog.product.dto.ProductSummaryResponse;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.service.OfferService;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.dto.CreateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantConflictException;
import com.ashenox.starter.catalog.product.variant.repository.VariantCharacteristicRepository;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import com.ashenox.starter.shared.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CharacteristicRepository characteristicRepository;
    private final ProductCharacteristicRepository productCharacteristicRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantCharacteristicRepository variantCharacteristicRepository;
    private final ProductVariantService productVariantService;
    private final OfferService offerService;

    @Override
    @Transactional(readOnly = true)
    public ProductPageResponse list(int page, int size, String nombre, Long categoriaId, Long subcategoriaId) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("La página o el tamaño solicitado no es válido.");
        }
        if ((categoriaId != null && categoriaId <= 0) || (subcategoriaId != null && subcategoriaId <= 0)) {
            throw new BusinessException("Los filtros de categoría y subcategoría deben ser positivos.");
        }
        if (categoriaId != null && subcategoriaId != null) {
            Subcategory subcategory = subcategoryRepository.findById(subcategoriaId)
                    .orElseThrow(() -> new BusinessException("La subcategoría no pertenece a la categoría indicada."));
            if (!subcategory.getCategory().getId().equals(categoriaId)) {
                throw new BusinessException("La subcategoría no pertenece a la categoría indicada.");
            }
        }
        String pattern = null;
        if (nombre != null) {
            String trimmed = nombre.strip();
            if (trimmed.isEmpty()) {
                throw new BusinessException("El nombre de búsqueda no puede estar vacío.");
            }
            pattern = "%" + trimmed.replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%";
        }
        Page<Product> result = productRepository.findPageByFilters(pattern, categoriaId, subcategoriaId,
                PageRequest.of(page, size));
        List<Long> productIds = result.getContent().stream().map(Product::getId).toList();
        List<ProductVariant> pageVariants = productIds.isEmpty() ? List.of()
                : productVariantRepository.findByProductIdInOrderByIdAsc(productIds);
        Map<Long, Long> owners = pageVariants.stream().collect(Collectors.toMap(
                ProductVariant::getId, variant -> variant.getProduct().getId()));
        Map<Long, List<ProductVariantResponse>> variantGroups = productVariantService.responses(pageVariants).stream()
                .collect(Collectors.groupingBy(value -> owners.get(value.id())));
        return new ProductPageResponse(result.getContent().stream()
                .map(product -> ProductSummaryResponse.from(product,
                        variantGroups.getOrDefault(product.getId(), List.of()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        boolean nested = request.varianteInicial() != null;
        if (nested == (request.precioReferencia() != null || request.disponible() != null)) {
            throw new BusinessException("Indique la variante inicial o precio y disponibilidad heredados.");
        }
        if (!nested && (request.precioReferencia() == null || request.disponible() == null)) {
            throw new BusinessException("Precio y disponibilidad son obligatorios.");
        }
        CreateVariantRequest initial = nested ? request.varianteInicial()
                : new CreateVariantRequest(null, request.precioReferencia(), request.disponible(), null, null, null);
        Category category = categoryRepository.lockById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        if (!category.isActivo()) {
            throw new ProductCategoryInactiveException();
        }
        Subcategory subcategory = request.subcategoriaId() == null ? null
                : lockAssignableSubcategory(request.subcategoriaId(), category);

        List<ProductCharacteristicRequest> requested = request.caracteristicas() == null
                ? List.of() : request.caracteristicas();
        Set<Long> ids = new HashSet<>();
        for (ProductCharacteristicRequest item : requested) {
            if (item == null || item.caracteristicaId() == null || item.caracteristicaId() <= 0
                    || item.valor() == null || item.valor().isBlank() || item.valor().length() > 500
                    || item.unidad() != null && item.unidad().length() > 100) {
                throw new BusinessException("Hay datos inválidos en las características del producto.");
            }
            if (!ids.add(item.caracteristicaId())) {
                throw new BusinessException("Una característica no puede repetirse en el producto.");
            }
        }
        List<Characteristic> definitions = new ArrayList<>();
        for (Long id : ids.stream().sorted().toList()) {
            definitions.add(characteristicRepository.lockById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("La característica solicitada no existe.")));
        }
        if (initial.caracteristicaId() != null && ids.contains(initial.caracteristicaId())) {
            throw new ProductVariantConflictException("El atributo variable no puede ser compartido por el producto.");
        }

        Product product = Product.builder()
                .category(category)
                .subcategory(subcategory)
                .nombre(request.nombre().strip())
                .descripcion(request.descripcion())
                .activo(true)
                .destacado(false)
                .build();
        Product saved = productRepository.saveAndFlush(product);
        ProductVariantResponse variant = productVariantService.createInitial(saved, initial);
        List<ProductCharacteristic> associations = new ArrayList<>();
        for (ProductCharacteristicRequest item : requested) {
            Characteristic definition = definitions.stream()
                    .filter(candidate -> candidate.getId().equals(item.caracteristicaId())).findFirst().orElseThrow();
            String unit = item.unidad() == null ? null : item.unidad().strip();
            associations.add(ProductCharacteristic.builder()
                    .id(new ProductCharacteristicId(saved.getId(), definition.getId()))
                    .product(saved).characteristic(definition).valor(item.valor().strip())
                    .unidad(unit == null || unit.isEmpty() ? null : unit).build());
        }
        if (!associations.isEmpty()) {
            productCharacteristicRepository.saveAllAndFlush(associations);
        }
        return ProductResponse.from(saved, associations.stream()
                .map(ProductCharacteristicResponse::from).toList(), List.of(variant));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        return response(product);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        // Las nuevas asignaciones bloquean categoría, subcategoría y producto, en ese orden.
        // Esto coordina la desactivación del padre y el borrado de la subcategoría.
        boolean assigning = request.subcategoriaIdPresente() && request.subcategoriaId() != null;
        Category category = categoryRepository.lockById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        Subcategory assigned = assigning ? lockAssignableSubcategory(request.subcategoriaId(), category) : null;
        Product product = productRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        List<ProductVariant> variants = productVariantRepository.findByProductIdOrderByIdAsc(id);
        if (variants.isEmpty()) throw new IllegalStateException("El producto no tiene variante inicial.");
        if (variants.size() > 1 && (request.precioReferenciaPresente() || request.disponiblePresente())) {
            throw new ProductVariantConflictException("El precio y la disponibilidad se editan por variante.");
        }
        if (variants.size() == 1 && (request.precioReferenciaPresente() && request.precioReferencia() == null
                || request.disponiblePresente() && request.disponible() == null)) {
            throw new BusinessException("Precio y disponibilidad no pueden ser null.");
        }
        if (!request.subcategoriaIdPresente() && product.getSubcategory() != null
                && !product.getSubcategory().getCategory().getId().equals(category.getId())) {
            throw new BusinessException("Al cambiar la categoría debe indicar una subcategoría compatible o null.");
        }

        if (request.caracteristicasPresentes()) {
            List<ProductCharacteristicRequest> requested = request.caracteristicas();
            if (requested == null) {
                throw new BusinessException("Las características no pueden ser null.");
            }
            Set<Long> ids = new HashSet<>();
            for (ProductCharacteristicRequest item : requested) {
                if (item == null || item.caracteristicaId() == null || item.caracteristicaId() <= 0
                        || item.valor() == null || item.valor().isBlank() || item.valor().length() > 500
                        || item.unidad() != null && item.unidad().length() > 100) {
                    throw new BusinessException("Hay datos inválidos en las características del producto.");
                }
                if (!ids.add(item.caracteristicaId())) {
                    throw new BusinessException("Una característica no puede repetirse en el producto.");
                }
            }
            Set<Long> variableIds = variantCharacteristicRepository.findByVariantProductId(id).stream()
                    .map(item -> item.getCharacteristic().getId()).collect(Collectors.toSet());
            if (ids.stream().anyMatch(variableIds::contains)) {
                throw new ProductVariantConflictException("El atributo variable no puede ser compartido por el producto.");
            }
            Map<Long, Characteristic> definitions = new java.util.HashMap<>();
            for (Long characteristicId : ids.stream().sorted().toList()) {
                definitions.put(characteristicId, characteristicRepository.lockById(characteristicId)
                        .orElseThrow(() -> new ResourceNotFoundException("La característica solicitada no existe.")));
            }
            Map<Long, ProductCharacteristic> existing = productCharacteristicRepository
                    .findByProductIdWithCharacteristic(id).stream()
                    .collect(Collectors.toMap(value -> value.getCharacteristic().getId(), Function.identity()));
            List<ProductCharacteristic> removed = existing.entrySet().stream()
                    .filter(entry -> !ids.contains(entry.getKey())).map(Map.Entry::getValue).toList();
            productCharacteristicRepository.deleteAll(removed);
            List<ProductCharacteristic> changed = new ArrayList<>();
            for (ProductCharacteristicRequest item : requested) {
                ProductCharacteristic association = existing.get(item.caracteristicaId());
                if (association == null) {
                    association = ProductCharacteristic.builder()
                            .id(new ProductCharacteristicId(id, item.caracteristicaId()))
                            .product(product).characteristic(definitions.get(item.caracteristicaId())).build();
                }
                association.setValor(item.valor().strip());
                String unit = item.unidad() == null ? null : item.unidad().strip();
                association.setUnidad(unit == null || unit.isEmpty() ? null : unit);
                changed.add(association);
            }
            productCharacteristicRepository.flush();
            productCharacteristicRepository.saveAllAndFlush(changed);
        }

        product.setCategory(category);
        if (request.subcategoriaIdPresente()) {
            product.setSubcategory(assigned);
        }
        product.setNombre(request.nombre().strip());
        product.setDescripcion(request.descripcion());
        ProductVariant variant = variants.getFirst();
        if (variants.size() == 1) {
            if (request.precioReferenciaPresente()) {
                offerService.validateBasePrice(variant.getId(), request.precioReferencia());
                variant.setPrecioReferencia(request.precioReferencia());
            }
            if (request.disponiblePresente()) {
                variant.setDisponible(request.disponible());
            }
        }
        Product saved = productRepository.saveAndFlush(product);
        if (variants.size() == 1) productVariantRepository.saveAndFlush(variant);
        return response(saved);
    }

    @Override
    @Transactional
    public ProductResponse deactivate(Long id) {
        return setActive(id, false);
    }

    @Override
    @Transactional
    public ProductResponse reactivate(Long id) {
        return setActive(id, true);
    }

    private ProductResponse setActive(Long id, boolean active) {
        Product product = productRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        product.setActivo(active);
        return response(productRepository.saveAndFlush(product));
    }

    private ProductResponse response(Product product) {
        List<ProductVariantResponse> variants = productVariantService.responses(
                productVariantRepository.findByProductIdOrderByIdAsc(product.getId()));
        return ProductResponse.from(product, productCharacteristicRepository
                .findByProductIdWithCharacteristic(product.getId()).stream()
                .map(ProductCharacteristicResponse::from).toList(), variants);
    }

    private Subcategory lockAssignableSubcategory(Long id, Category category) {
        Subcategory subcategory = subcategoryRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La subcategoría solicitada no existe."));
        validateAssignableSubcategory(subcategory, category);
        return subcategory;
    }

    private void validateAssignableSubcategory(Subcategory subcategory, Category category) {
        if (!subcategory.getCategory().getId().equals(category.getId())) {
            throw new BusinessException("La subcategoría no pertenece a la categoría indicada.");
        }
        if (!category.isActivo()) {
            throw new ProductCategoryInactiveException();
        }
    }
}
