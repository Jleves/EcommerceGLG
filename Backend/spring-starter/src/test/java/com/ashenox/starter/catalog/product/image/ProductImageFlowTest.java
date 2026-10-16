package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.service.ImageStorageUnavailableException;
import com.ashenox.starter.catalog.product.image.service.ProductImageConflictException;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import com.ashenox.starter.catalog.product.image.storage.S3ImageStorage;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
                .precioReferencia(BigDecimal.ONE).disponible(true).build()).getId();
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
        FakeStorage() { super(null, null); }
        @Override public void put(String key, byte[] webp) {
            if (putCount.incrementAndGet() == failOnPut.get()) throw new IllegalStateException("simulated S3 failure");
            objects.put(key, webp);
        }
        @Override public void delete(String key) { objects.remove(key); }
    }
}
