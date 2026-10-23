package com.ashenox.starter.catalog.product.variant.repository;

import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VariantCharacteristicRepository extends JpaRepository<VariantCharacteristic, Long> {
    List<VariantCharacteristic> findByVariantProductId(Long productId);
}
