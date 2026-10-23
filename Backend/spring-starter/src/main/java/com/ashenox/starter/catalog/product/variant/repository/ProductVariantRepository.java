package com.ashenox.starter.catalog.product.variant.repository;

import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findByProductId(Long productId);
    List<ProductVariant> findByProductIdIn(Collection<Long> productIds);
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
    List<ProductVariant> findByProductIdInOrderByIdAsc(Collection<Long> productIds);
    boolean existsBySkuNormalizadoAndIdNot(String skuNormalizado, Long id);
}
