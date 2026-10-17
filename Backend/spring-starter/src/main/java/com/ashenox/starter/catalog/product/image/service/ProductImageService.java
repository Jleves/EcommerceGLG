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
import java.util.HashSet;
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
    private final ProductImageUrlService urls;
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

    public ProductImageResponse setPrimary(Long productId, Long imageId) {
        return transactions.execute(status -> {
            lockProduct(productId);
            ProductImage selected = publishedImage(productId, imageId);
            if (selected.isPrimary()) return response(selected);
            images.findByProductIdAndStatusOrderByPositionAscIdAsc(productId, "PUBLISHED")
                    .stream().filter(ProductImage::isPrimary).forEach(image -> image.setPrimary(false));
            images.flush();
            selected.setPrimary(true);
            images.flush();
            return response(selected);
        });
    }

    public List<ProductImageResponse> reorder(Long productId, List<Long> imageIds) {
        return transactions.execute(status -> {
            lockProduct(productId);
            List<ProductImage> current = images.findByProductIdAndStatusOrderByPositionAscIdAsc(productId, "PUBLISHED");
            if (imageIds == null || imageIds.size() != current.size()
                    || new HashSet<>(imageIds).size() != current.size()
                    || !new HashSet<>(imageIds).equals(current.stream().map(ProductImage::getId)
                    .collect(java.util.stream.Collectors.toSet()))) {
                throw new InvalidProductImageException("El orden debe contener todos los IDs de fotos una sola vez.");
            }
            var byId = current.stream().collect(java.util.stream.Collectors.toMap(ProductImage::getId, image -> image));
            for (int index = 0; index < imageIds.size(); index++) byId.get(imageIds.get(index)).setPosition(index + 1);
            images.flush();
            return imageIds.stream().map(id -> response(byId.get(id))).toList();
        });
    }

    public void delete(Long productId, Long imageId) {
        transactions.executeWithoutResult(status -> {
            var product = lockProduct(productId);
            ProductImage image = publishedImage(productId, imageId);
            boolean wasPrimary = image.isPrimary();
            scheduleCleanup(product, imageId, image.getThumbnailKey(), image.getDetailKey(), LocalDateTime.now());
            images.delete(image);
            images.flush();
            List<ProductImage> remaining = images.findByProductIdAndStatusOrderByPositionAscIdAsc(productId, "PUBLISHED");
            for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPosition(index + 1);
            if (wasPrimary && !remaining.isEmpty()) remaining.getFirst().setPrimary(true);
            images.flush();
        });
    }

    private void scheduleCleanup(com.ashenox.starter.catalog.product.model.Product product, Long imageId,
                                 String thumbnailKey, String detailKey, LocalDateTime due) {
        ImageStorageOperation operation = new ImageStorageOperation();
        operation.setProduct(product);
        operation.setImageId(imageId);
        operation.setThumbnailKey(thumbnailKey);
        operation.setDetailKey(detailKey);
        operation.setStatus("CLEANUP_REQUIRED");
        operation.setNextExecutionAt(due);
        operations.save(operation);
    }

    private com.ashenox.starter.catalog.product.model.Product lockProduct(Long productId) {
        return products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));
    }

    private ProductImage publishedImage(Long productId, Long imageId) {
        ProductImage image = images.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("La foto no existe."));
        if (!image.getProduct().getId().equals(productId) || !"PUBLISHED".equals(image.getStatus())) {
            throw new ResourceNotFoundException("La foto no existe.");
        }
        return image;
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
        operation.setNextExecutionAt(LocalDateTime.now().plus(properties.getImages().getAbandonedUploadGrace()));
        operations.saveAndFlush(operation);
        return new Reservation(image.getId(), operation.getId(), thumb, detail);
    }

    private ProductImageResponse publish(Long productId, Reservation reserved) {
        lockProduct(productId);
        ImageStorageOperation operation = operations.lockById(reserved.operationId()).orElseThrow();
        if (!"PENDING_UPLOAD".equals(operation.getStatus())) {
            throw new ProductImageConflictException("La carga ya no está pendiente.");
        }
        ProductImage image = images.findById(reserved.imageId()).orElseThrow();
        image.setPosition(images.maxPublishedPosition(productId) + 1);
        image.setPrimary(images.countByProductIdAndStatus(productId, "PUBLISHED") == 0);
        image.setStatus("PUBLISHED");
        operation.setStatus("COMPLETE");
        operation.setNextExecutionAt(null);
        images.saveAndFlush(image);
        operations.save(operation);
        return response(image);
    }

    private void markForCleanup(Reservation reserved) {
        Long productId = operations.findById(reserved.operationId()).orElseThrow().getProduct().getId();
        lockProduct(productId);
        ImageStorageOperation operation = operations.lockById(reserved.operationId()).orElseThrow();
        if (!"PENDING_UPLOAD".equals(operation.getStatus())) return;
        operation.setStatus("CLEANUP_REQUIRED");
        operation.setAttempts(operation.getAttempts() + 1);
        operation.setNextExecutionAt(LocalDateTime.now());
        operations.save(operation);
        images.findById(reserved.imageId()).filter(image -> "PENDING".equals(image.getStatus()))
                .ifPresent(images::delete);
    }

    private ProductImageResponse response(ProductImage image) {
        return urls.toResponse(image);
    }
}
