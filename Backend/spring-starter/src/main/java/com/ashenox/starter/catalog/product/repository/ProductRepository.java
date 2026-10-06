package com.ashenox.starter.catalog.product.repository;

import com.ashenox.starter.catalog.product.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = "select product from Product product where :pattern is null or lower(product.nombre) like lower(:pattern) escape '!' order by product.nombre asc, product.id asc",
            countQuery = "select count(product) from Product product where :pattern is null or lower(product.nombre) like lower(:pattern) escape '!'")
    Page<Product> findPageByName(String pattern, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :id")
    Optional<Product> lockById(Long id);
}
