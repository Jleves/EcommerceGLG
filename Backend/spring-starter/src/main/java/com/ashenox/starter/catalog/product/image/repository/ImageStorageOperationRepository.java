package com.ashenox.starter.catalog.product.image.repository;

import com.ashenox.starter.catalog.product.image.model.ImageStorageOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageStorageOperationRepository extends JpaRepository<ImageStorageOperation, Long> {
}
