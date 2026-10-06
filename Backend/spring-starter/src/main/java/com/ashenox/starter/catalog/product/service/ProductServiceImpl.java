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
import com.ashenox.starter.catalog.product.repository.ProductRepository;
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

    @Override
    @Transactional(readOnly = true)
    public ProductPageResponse list(int page, int size, String nombre) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("La página o el tamaño solicitado no es válido.");
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
        Page<Product> result = productRepository.findPageByName(pattern, PageRequest.of(page, size));
        return new ProductPageResponse(result.getContent().stream().map(ProductSummaryResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Category category = categoryRepository.lockById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        if (!category.isActivo()) {
            throw new ProductCategoryInactiveException();
        }

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

        Product product = Product.builder()
                .category(category)
                .nombre(request.nombre().strip())
                .descripcion(request.descripcion())
                .precioReferencia(request.precioReferencia())
                .disponible(request.disponible())
                .activo(true)
                .destacado(false)
                .build();
        Product saved = productRepository.saveAndFlush(product);
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
                .map(ProductCharacteristicResponse::from).toList());
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
        Product product = productRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        Category category = categoryRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));

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
        product.setNombre(request.nombre().strip());
        product.setDescripcion(request.descripcion());
        product.setPrecioReferencia(request.precioReferencia());
        product.setDisponible(request.disponible());
        return response(productRepository.saveAndFlush(product));
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
        return ProductResponse.from(product, productCharacteristicRepository
                .findByProductIdWithCharacteristic(product.getId()).stream()
                .map(ProductCharacteristicResponse::from).toList());
    }
}
