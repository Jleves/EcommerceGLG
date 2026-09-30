package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductResponse;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Category category = categoryRepository.lockById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        if (!category.isActivo()) {
            throw new ProductCategoryInactiveException();
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
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }
}
