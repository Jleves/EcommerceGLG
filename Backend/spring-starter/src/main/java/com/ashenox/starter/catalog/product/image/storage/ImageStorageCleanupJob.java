package com.ashenox.starter.catalog.product.image.storage;

import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ImageStorageCleanupJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageStorageCleanupJob.class);

    private final ImageStorageOperationRepository operations;
    private final ProductImageRepository images;
    private final ProductRepository products;
    private final S3ImageStorage storage;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    @Scheduled(fixedDelayString = "${app.images.cleanup-interval-ms:300000}",
            initialDelayString = "${app.images.cleanup-interval-ms:300000}")
    public void run() {
        List<Long> due = operations.findDueIds(LocalDateTime.now(),
                PageRequest.of(0, properties.getImages().getCleanupBatchSize()));
        for (Long id : due) {
            try {
                transactions.executeWithoutResult(status -> process(id));
            } catch (RuntimeException failure) {
                LOGGER.error("image_cleanup_transaction_failed operationId={}", id, failure);
            }
        }
        long waiting = operations.countByStatus("CLEANUP_REQUIRED")
                + operations.countByStatus("PENDING_UPLOAD");
        if (waiting > 0) LOGGER.info("image_cleanup_pending count={} processed={}", waiting, due.size());
    }

    private void process(Long id) {
        var snapshot = operations.findById(id).orElse(null);
        if (snapshot == null) return;
        Long productId = snapshot.getProduct().getId();
        products.lockById(productId).orElseThrow();
        var operation = operations.lockById(id).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        if (!("PENDING_UPLOAD".equals(operation.getStatus())
                || "CLEANUP_REQUIRED".equals(operation.getStatus()))
                || operation.getNextExecutionAt() == null
                || operation.getNextExecutionAt().isAfter(now)) return;

        ProductImage image = images.findById(operation.getImageId()).orElse(null);
        if (image != null && "PUBLISHED".equals(image.getStatus())
                && operation.getThumbnailKey().equals(image.getThumbnailKey())) {
            recordFailure(operation, new IllegalStateException("Las claves siguen publicadas"), now);
            return;
        }

        // A persisted PENDING_UPLOAD is abandoned after its grace period. A repeated
        // delete is safe if a prior run removed an object before the DB commit.
        operation.setStatus("CLEANUP_REQUIRED");
        RuntimeException failure = null;
        try { storage.delete(operation.getThumbnailKey()); }
        catch (RuntimeException exception) { failure = exception; }
        try { storage.delete(operation.getDetailKey()); }
        catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) {
            recordFailure(operation, failure, now);
            return;
        }
        if (image != null && "PENDING".equals(image.getStatus())
                && operation.getThumbnailKey().equals(image.getThumbnailKey())) {
            images.delete(image);
        }
        operation.setStatus("COMPLETE");
        operation.setNextExecutionAt(null);
        LOGGER.info("image_cleanup_complete operationId={} productId={} imageId={}",
                id, productId, operation.getImageId());
    }

    private void recordFailure(com.ashenox.starter.catalog.product.image.model.ImageStorageOperation operation,
                               RuntimeException failure, LocalDateTime now) {
        int attempts = operation.getAttempts() + 1;
        operation.setAttempts(attempts);
        Duration base = properties.getImages().getCleanupRetryBase();
        Duration maximum = properties.getImages().getCleanupRetryMax();
        Duration delay = base.multipliedBy(1L << Math.min(attempts - 1, 20));
        if (delay.compareTo(maximum) > 0) delay = maximum;
        operation.setNextExecutionAt(now.plus(delay));
        LOGGER.warn("image_cleanup_retry operationId={} attempts={} nextExecutionAt={}",
                operation.getId(), attempts, operation.getNextExecutionAt(), failure);
    }
}
