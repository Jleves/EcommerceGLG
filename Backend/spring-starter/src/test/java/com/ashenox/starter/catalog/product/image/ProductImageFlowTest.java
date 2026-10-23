package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.model.ImageStorageOperation;
import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.catalog.product.image.service.ImageStorageUnavailableException;
import com.ashenox.starter.catalog.product.image.service.ProductImageConflictException;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import com.ashenox.starter.catalog.product.image.storage.S3ImageStorage;
import com.ashenox.starter.catalog.product.image.storage.ImageStorageCleanupJob;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:productimagetest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@Import(ProductImageFlowTest.FakeStorageConfig.class)
class ProductImageFlowTest {
    @Autowired ProductImageService service;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired ProductImageRepository images;
    @Autowired ImageStorageOperationRepository operations;
    @Autowired FakeStorage storage;
    @Autowired ImageStorageCleanupJob cleanup;

    @Test
    void reorderPrimaryAndDeletePromotesFirstRemaining() throws Exception {
        Long id = product();
        var first = service.upload(id, file());
        var second = service.upload(id, file());
        var third = service.upload(id, file());
        assertThatThrownBy(() -> service.reorder(id, List.of(first.id(), first.id(), third.id())))
                .isInstanceOf(com.ashenox.starter.catalog.product.image.service.InvalidProductImageException.class);
        assertThatThrownBy(() -> service.reorder(id, List.of(first.id(), second.id())))
                .isInstanceOf(com.ashenox.starter.catalog.product.image.service.InvalidProductImageException.class);
        assertThat(service.reorder(id, List.of(third.id(), first.id(), second.id())))
                .extracting(com.ashenox.starter.catalog.product.image.dto.ProductImageResponse::id)
                .containsExactly(third.id(), first.id(), second.id());
        service.setPrimary(id, second.id());
        assertThat(service.list(id).stream().filter(image -> image.primary()).map(image -> image.id()))
                .containsExactly(second.id());
        service.delete(id, second.id());
        assertThat(service.list(id)).extracting(com.ashenox.starter.catalog.product.image.dto.ProductImageResponse::id)
                .containsExactly(third.id(), first.id());
        assertThat(service.list(id).getFirst().primary()).isTrue();
        assertThat(storage.objects).containsKey(key(second.thumbnailUrl()));
        cleanup.run();
        assertThat(storage.objects).doesNotContainKeys(key(second.thumbnailUrl()), key(second.detailUrl()));
    }

    @Test
    void failedDeleteCleanupRetriesWithoutRestoringPhoto() throws Exception {
        Long id = product();
        var original = service.upload(id, file());
        service.delete(id, original.id());
        assertThat(service.list(id)).isEmpty();
        var pending = operations.findAll().stream().filter(op -> op.getImageId().equals(original.id())
                && "CLEANUP_REQUIRED".equals(op.getStatus())).findFirst().orElseThrow();
        storage.failDeleteKey.set(key(original.detailUrl()));
        cleanup.run();
        assertThat(operations.findById(pending.getId()).orElseThrow().getAttempts()).isEqualTo(1);
        storage.failDeleteKey.set(null);
        var retry = operations.findById(pending.getId()).orElseThrow();
        retry.setNextExecutionAt(LocalDateTime.now().minusSeconds(1));
        operations.saveAndFlush(retry);
        cleanup.run();
        assertThat(operations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETE");
        assertThat(service.list(id)).isEmpty();
        assertThat(storage.objects).doesNotContainKeys(key(original.thumbnailUrl()), key(original.detailUrl()));
        cleanup.run();
        assertThat(operations.findById(pending.getId()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void legacyPendingReplacementIsCleanedWithoutDeletingPublishedPhoto() throws Exception {
        Long id = product();
        var original = service.upload(id, file());
        ImageStorageOperation pending = new ImageStorageOperation();
        pending.setProduct(products.findById(id).orElseThrow());
        pending.setImageId(original.id());
        pending.setThumbnailKey("products/" + id + "/images/" + original.id() + "/abandoned/thumb.webp");
        pending.setDetailKey("products/" + id + "/images/" + original.id() + "/abandoned/detail.webp");
        pending.setStatus("PENDING_UPLOAD");
        pending.setNextExecutionAt(LocalDateTime.now().plusHours(1));
        operations.saveAndFlush(pending);
        storage.objects.put(pending.getThumbnailKey(), new byte[]{1});
        cleanup.run();
        assertThat(storage.objects).containsKey(pending.getThumbnailKey());
        pending.setNextExecutionAt(LocalDateTime.now().minusSeconds(1));
        operations.saveAndFlush(pending);
        cleanup.run();
        assertThat(storage.objects).doesNotContainKey(pending.getThumbnailKey());
        assertThat(operations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETE");
        assertThat(service.list(id)).containsExactly(original);
    }

    @Test
    void simultaneousPrimaryChangesKeepExactlyOnePrimary() throws Exception {
        Long id = product();
        var first = service.upload(id, file());
        var second = service.upload(id, file());
        var third = service.upload(id, file());
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { gate.await(); service.setPrimary(id, second.id()); return true; });
            var b = pool.submit(() -> { gate.await(); service.setPrimary(id, third.id()); return true; });
            gate.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.list(id).stream().filter(image -> image.primary()).count()).isEqualTo(1);
        assertThat(service.list(id)).hasSize(3);
    }

    @Test
    void abandonedNewUploadReleasesReservedSlot() throws Exception {
        Long id = product();
        ProductImage pending = new ProductImage();
        pending.setProduct(products.findById(id).orElseThrow());
        pending.setPosition(0);
        pending.setPrimary(false);
        pending.setRevision(UUID.randomUUID().toString());
        pending.setStatus("PENDING");
        images.saveAndFlush(pending);
        String prefix = "products/" + id + "/images/" + pending.getId() + "/" + pending.getRevision() + "/";
        pending.setThumbnailKey(prefix + "thumb.webp");
        pending.setDetailKey(prefix + "detail.webp");
        images.saveAndFlush(pending);
        ImageStorageOperation operation = new ImageStorageOperation();
        operation.setProduct(pending.getProduct());
        operation.setImageId(pending.getId());
        operation.setThumbnailKey(pending.getThumbnailKey());
        operation.setDetailKey(pending.getDetailKey());
        operation.setStatus("PENDING_UPLOAD");
        operation.setNextExecutionAt(LocalDateTime.now().minusSeconds(1));
        operations.saveAndFlush(operation);
        storage.objects.put(pending.getThumbnailKey(), new byte[]{1});
        cleanup.run();
        assertThat(images.findById(pending.getId())).isEmpty();
        assertThat(storage.objects).doesNotContainKey(pending.getThumbnailKey());
        assertThat(operations.findById(operation.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETE");
        assertThat(service.upload(id, file()).primary()).isTrue();
    }

    @Test
    void uploadPublishesTwoKeysAndFirstPhotoIsPrimary() throws Exception {
        Long id = product();
        var result = service.upload(id, file());
        assertThat(result.primary()).isTrue();
        assertThat(result.position()).isEqualTo(1);
        assertThat(result.thumbnailUrl()).startsWith("https://").endsWith("/thumb.webp");
        assertThat(result.detailUrl()).endsWith("/detail.webp");
        assertThat(storage.objects).containsKeys(key(result.thumbnailUrl()), key(result.detailUrl()));
        assertThat(service.list(id)).containsExactly(result);
        assertThat(operations.findAll().stream().filter(op -> op.getImageId().equals(result.id()))
                .findFirst().orElseThrow().getStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void secondS3FailureLeavesNoVisiblePhotoAndPersistsCleanupKeys() throws Exception {
        Long id = product();
        storage.failOnPut.set(storage.putCount.get() + 2);
        assertThatThrownBy(() -> service.upload(id, file()))
                .isInstanceOf(ImageStorageUnavailableException.class);
        assertThat(service.list(id)).isEmpty();
        assertThat(images.countByProductId(id)).isZero();
        assertThat(operations.findAll().stream().filter(op -> op.getProduct().getId().equals(id))
                .findFirst().orElseThrow().getStatus()).isEqualTo("CLEANUP_REQUIRED");
        storage.failOnPut.set(0);
    }

    @Test
    void sixthUploadIsRejectedWithoutWritingMoreObjects() throws Exception {
        Long id = product();
        for (int i = 0; i < 5; i++) service.upload(id, file());
        int count = storage.objects.size();
        assertThatThrownBy(() -> service.upload(id, file())).isInstanceOf(ProductImageConflictException.class);
        assertThat(storage.objects).hasSize(count);
        assertThat(service.list(id)).hasSize(5);
        service.delete(id, service.list(id).getFirst().id());
        assertThat(service.list(id)).hasSize(4);
        assertThat(service.list(id)).extracting(image -> image.position()).containsExactly(1, 2, 3, 4);
        assertThat(service.list(id).getFirst().primary()).isTrue();
        service.upload(id, file());
        assertThat(service.list(id)).hasSize(5);
    }

    @Test
    void concurrentUploadsCannotExceedFivePhotos() throws Exception {
        Long id = product();
        for (int i = 0; i < 4; i++) service.upload(id, file());
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attemptUpload(id, gate));
            var second = pool.submit(() -> attemptUpload(id, gate));
            gate.countDown();
            int success = first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS);
            assertThat(success).isEqualTo(1);
            assertThat(service.list(id)).hasSize(5);
        }
    }

    private int attemptUpload(Long id, CountDownLatch gate) throws Exception {
        gate.await();
        try {
            service.upload(id, file());
            return 1;
        } catch (ProductImageConflictException expected) {
            return 0;
        }
    }

    private String key(String url) { return url.substring(url.indexOf("/products/") + 1); }

    private Long product() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Imagen " + suffix)
                .nombreNormalizado("imagen-" + suffix).icono("test").build());
        return products.saveAndFlush(Product.builder().category(category).nombre("Imagen " + suffix)
                .build()).getId();
    }

    private MockMultipartFile file() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "test.png", "image/png", out.toByteArray());
    }

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean @Primary FakeStorage fakeStorage() { return new FakeStorage(); }
    }

    static class FakeStorage extends S3ImageStorage {
        final ConcurrentHashMap<String, byte[]> objects = new ConcurrentHashMap<>();
        final AtomicInteger putCount = new AtomicInteger();
        final AtomicInteger failOnPut = new AtomicInteger();
        final AtomicReference<String> failDeleteKey = new AtomicReference<>();
        FakeStorage() { super(null, null); }
        @Override public void put(String key, byte[] webp) {
            if (putCount.incrementAndGet() == failOnPut.get()) throw new IllegalStateException("simulated S3 failure");
            objects.put(key, webp);
        }
        @Override public void delete(String key) {
            if (key.equals(failDeleteKey.get())) throw new IllegalStateException("simulated delete failure");
            objects.remove(key);
        }
    }
}
