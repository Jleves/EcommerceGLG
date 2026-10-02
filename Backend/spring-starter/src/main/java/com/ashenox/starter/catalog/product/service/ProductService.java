package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductResponse;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Long id, UpdateProductRequest request);
}
