package com.ashenox.starter.catalog.product.variant.repository;

import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;

public interface VariantCharacteristicRepository extends JpaRepository<VariantCharacteristic, Long> {
    List<VariantCharacteristic> findByVariantProductId(Long productId);
    @Query("select vc from VariantCharacteristic vc join fetch vc.characteristic where vc.variant.id in :variantIds")
    List<VariantCharacteristic> findByVariantIdsWithCharacteristic(Collection<Long> variantIds);
    long countByCharacteristicId(Long characteristicId);
    boolean existsByCharacteristicId(Long characteristicId);
    @Query("select vc.characteristic.id, count(vc) from VariantCharacteristic vc group by vc.characteristic.id")
    List<Object[]> countAllByCharacteristic();
}
