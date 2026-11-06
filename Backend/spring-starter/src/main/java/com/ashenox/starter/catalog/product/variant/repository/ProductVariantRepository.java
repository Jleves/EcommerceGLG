package com.ashenox.starter.catalog.product.variant.repository;

import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @Query("select variant.product.id from ProductVariant variant where variant.id = :id")
    Optional<Long> findProductIdByVariantId(Long id);
    Optional<ProductVariant> findByProductId(Long productId);
    List<ProductVariant> findByProductIdIn(Collection<Long> productIds);
    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);
    List<ProductVariant> findByProductIdInOrderByIdAsc(Collection<Long> productIds);
    @Query("select variant from ProductVariant variant where variant.product.id in :productIds and variant.activo = true order by variant.product.id, variant.id")
    List<ProductVariant> findActiveByProductIds(Collection<Long> productIds);
    List<ProductVariant> findByProductIdAndActivoTrueOrderByIdAsc(Long productId);
    boolean existsBySkuNormalizadoAndIdNot(String skuNormalizado, Long id);
}
