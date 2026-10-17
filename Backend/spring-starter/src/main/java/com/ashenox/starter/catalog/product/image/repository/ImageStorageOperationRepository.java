package com.ashenox.starter.catalog.product.image.repository;

import com.ashenox.starter.catalog.product.image.model.ImageStorageOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImageStorageOperationRepository extends JpaRepository<ImageStorageOperation, Long> {
    long countByStatus(String status);

    @Query("select operation.id from ImageStorageOperation operation where operation.status in ('PENDING_UPLOAD', 'CLEANUP_REQUIRED') and operation.nextExecutionAt <= :now order by operation.nextExecutionAt, operation.id")
    List<Long> findDueIds(LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from ImageStorageOperation operation where operation.id = :id")
    Optional<ImageStorageOperation> lockById(Long id);
}
