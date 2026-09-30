package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductResponse;

public interface ProductService {
    ProductResponse create(CreateProductRequest request);
}
