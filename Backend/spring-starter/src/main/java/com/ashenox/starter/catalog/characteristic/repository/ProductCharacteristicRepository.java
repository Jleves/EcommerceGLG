package com.ashenox.starter.catalog.characteristic.repository;

import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristicId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductCharacteristicRepository extends JpaRepository<ProductCharacteristic, ProductCharacteristicId> {
    @Query("select pc from ProductCharacteristic pc join fetch pc.characteristic where pc.product.id = :productId order by pc.characteristic.id")
    List<ProductCharacteristic> findByProductIdWithCharacteristic(@Param("productId") Long productId);
}
