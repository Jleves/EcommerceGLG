package com.ashenox.starter.catalog.product.image.repository;

import com.ashenox.starter.catalog.product.image.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    long countByProductId(Long productId);
    long countByProductIdAndStatus(Long productId, String status);
    List<ProductImage> findByProductIdAndStatusOrderByPositionAscIdAsc(Long productId, String status);

    @Query("select coalesce(max(image.position), 0) from ProductImage image where image.product.id = :productId and image.status = 'PUBLISHED'")
    int maxPublishedPosition(Long productId);
}
