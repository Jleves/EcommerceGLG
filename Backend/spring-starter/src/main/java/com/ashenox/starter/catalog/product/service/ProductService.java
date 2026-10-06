package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductResponse;
import com.ashenox.starter.catalog.product.dto.ProductPageResponse;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;

public interface ProductService {
    ProductPageResponse list(int page, int size, String nombre);

    ProductResponse create(CreateProductRequest request);

    ProductResponse getById(Long id);

    ProductResponse update(Long id, UpdateProductRequest request);

    ProductResponse deactivate(Long id);

    ProductResponse reactivate(Long id);
}
