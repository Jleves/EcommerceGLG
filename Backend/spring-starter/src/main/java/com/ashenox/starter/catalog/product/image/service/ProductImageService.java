package com.ashenox.starter.catalog.product.image.service;

import com.ashenox.starter.catalog.product.image.dto.ProductImageResponse;
import com.ashenox.starter.catalog.product.image.model.ImageStorageOperation;
import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.storage.S3ImageStorage;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageStorageOperationRepository operations;
    private final ImageProcessor processor;
    private final S3ImageStorage storage;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    private record Reservation(Long imageId, Long operationId, String thumbnailKey, String detailKey) {}

    public List<ProductImageResponse> list(Long productId) {
        if (!products.existsById(productId)) throw new ResourceNotFoundException("El producto no existe.");
        return images.findByProductIdAndStatusOrderByPositionAscIdAsc(productId, "PUBLISHED")
                .stream().map(this::response).toList();
    }

    public ProductImageResponse upload(Long productId, MultipartFile file) {
        ImageProcessor.Variants variants = processor.validateAndGenerate(file);
        Reservation reserved = transactions.execute(status -> reserve(productId));
        try {
            storage.put(reserved.thumbnailKey(), variants.thumbnail());
            storage.put(reserved.detailKey(), variants.detail());
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> markForCleanup(reserved));
            throw new ImageStorageUnavailableException(failure);
        }
        try {
            return transactions.execute(status -> publish(productId, reserved));
        } catch (RuntimeException failure) {
            // Keep the persisted intent and pending row. A later reconciliation can inspect S3 and MySQL.
            throw new ImageStorageUnavailableException(failure);
        }
    }

    private Reservation reserve(Long productId) {
        var product = products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));
        if (images.countByProductId(productId) >= 5) {
            throw new ProductImageConflictException("El producto ya tiene cinco fotos o cargas pendientes.");
        }
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setPosition(0);
        image.setPrimary(false);
        image.setRevision(UUID.randomUUID().toString());
        image.setStatus("PENDING");
        images.saveAndFlush(image);

        String prefix = "products/" + productId + "/images/" + image.getId() + "/" + image.getRevision() + "/";
        String thumb = prefix + "thumb.webp";
        String detail = prefix + "detail.webp";
        image.setThumbnailKey(thumb);
        image.setDetailKey(detail);

        ImageStorageOperation operation = new ImageStorageOperation();
        operation.setProduct(product);
        operation.setImageId(image.getId());
        operation.setThumbnailKey(thumb);
        operation.setDetailKey(detail);
        operation.setStatus("PENDING_UPLOAD");
        operation.setNextExecutionAt(LocalDateTime.now());
        operations.saveAndFlush(operation);
        return new Reservation(image.getId(), operation.getId(), thumb, detail);
    }

    private ProductImageResponse publish(Long productId, Reservation reserved) {
        products.lockById(productId).orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));
        ProductImage image = images.findById(reserved.imageId()).orElseThrow();
        image.setPosition(images.maxPublishedPosition(productId) + 1);
        image.setPrimary(images.countByProductIdAndStatus(productId, "PUBLISHED") == 0);
        image.setStatus("PUBLISHED");
        ImageStorageOperation operation = operations.findById(reserved.operationId()).orElseThrow();
        operation.setStatus("COMPLETE");
        operation.setNextExecutionAt(null);
        images.saveAndFlush(image);
        operations.save(operation);
        return response(image);
    }

    private void markForCleanup(Reservation reserved) {
        ImageStorageOperation operation = operations.findById(reserved.operationId()).orElseThrow();
        operation.setStatus("CLEANUP_REQUIRED");
        operation.setAttempts(operation.getAttempts() + 1);
        operation.setNextExecutionAt(LocalDateTime.now());
        operations.save(operation);
        images.deleteById(reserved.imageId());
    }

    private ProductImageResponse response(ProductImage image) {
        String base = properties.getImages().getCloudFrontBaseUrl().toString();
        return new ProductImageResponse(image.getId(), image.getPosition(), image.isPrimary(),
                base + "/" + image.getThumbnailKey(), base + "/" + image.getDetailKey());
    }
}
