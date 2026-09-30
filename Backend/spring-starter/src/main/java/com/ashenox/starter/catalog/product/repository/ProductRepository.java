package com.ashenox.starter.catalog.product.repository;

import com.ashenox.starter.catalog.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
