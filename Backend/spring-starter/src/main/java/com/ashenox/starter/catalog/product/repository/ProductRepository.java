package com.ashenox.starter.catalog.product.repository;

import com.ashenox.starter.catalog.product.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    long countBySubcategoryId(Long subcategoryId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE products SET subcategory_id = NULL, updated_at = CURRENT_TIMESTAMP(6) WHERE subcategory_id = :id", nativeQuery = true)
    int clearSubcategoryAndTouchUpdatedAt(Long id);

    @Query(value = "select product from Product product where (:pattern is null or lower(product.nombre) like lower(:pattern) escape '!') and (:categoryId is null or product.category.id = :categoryId) and (:subcategoryId is null or product.subcategory.id = :subcategoryId) order by product.nombre asc, product.id asc",
            countQuery = "select count(product) from Product product where (:pattern is null or lower(product.nombre) like lower(:pattern) escape '!') and (:categoryId is null or product.category.id = :categoryId) and (:subcategoryId is null or product.subcategory.id = :subcategoryId)")
    Page<Product> findPageByFilters(String pattern, Long categoryId, Long subcategoryId, Pageable pageable);

    @Query(value = "select product from Product product where :pattern is null or lower(product.nombre) like lower(:pattern) escape '!' order by product.nombre asc, product.id asc",
            countQuery = "select count(product) from Product product where :pattern is null or lower(product.nombre) like lower(:pattern) escape '!'")
    Page<Product> findPageByName(String pattern, Pageable pageable);

    @Query(value = "select product from Product product where product.activo = true and exists (select variant.id from ProductVariant variant where variant.product = product and variant.activo = true) order by product.nombre asc, product.id asc",
            countQuery = "select count(product) from Product product where product.activo = true and exists (select variant.id from ProductVariant variant where variant.product = product and variant.activo = true)")
    Page<Product> findActivePage(Pageable pageable);

    @Query(value = "select product from Product product where product.activo = true and product.id <> :currentId and exists (select variant.id from ProductVariant variant where variant.product = product and variant.activo = true) and (product.category.id = :categoryId or exists (select pc.id from ProductCharacteristic pc where pc.product = product and lower(pc.characteristic.nombre) = 'marca' and lower(pc.valor) = :brand)) order by product.nombre asc, product.id asc",
            countQuery = "select count(product) from Product product where product.activo = true and product.id <> :currentId and exists (select variant.id from ProductVariant variant where variant.product = product and variant.activo = true) and (product.category.id = :categoryId or exists (select pc.id from ProductCharacteristic pc where pc.product = product and lower(pc.characteristic.nombre) = 'marca' and lower(pc.valor) = :brand))")
    Page<Product> findRelated(Long currentId, Long categoryId, String brand, Pageable pageable);

    Optional<Product> findByIdAndActivoTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id")
    Optional<Product> lockById(Long id);
}
