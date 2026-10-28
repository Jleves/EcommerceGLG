package com.ashenox.starter.catalog.characteristic.repository;

import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristicId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductCharacteristicRepository extends JpaRepository<ProductCharacteristic, ProductCharacteristicId> {
    @Query("select pc from ProductCharacteristic pc join fetch pc.characteristic where pc.product.id = :productId order by pc.characteristic.id")
    List<ProductCharacteristic> findByProductIdWithCharacteristic(@Param("productId") Long productId);

    @Query("select distinct pc.characteristic.id, pc.characteristic.nombre, pc.valor from ProductCharacteristic pc where pc.product.activo = true and exists (select variant.id from ProductVariant variant where variant.product = pc.product and variant.activo = true)")
    List<Object[]> findPublicFacetValues();

    @Query("select pc.characteristic.id, count(pc) from ProductCharacteristic pc group by pc.characteristic.id")
    List<Object[]> countAllByCharacteristic();

    long countByCharacteristic_Id(Long characteristicId);

    @Modifying
    @Query("delete from ProductCharacteristic pc where pc.characteristic.id = :characteristicId")
    void deleteByCharacteristicId(@Param("characteristicId") Long characteristicId);
}
