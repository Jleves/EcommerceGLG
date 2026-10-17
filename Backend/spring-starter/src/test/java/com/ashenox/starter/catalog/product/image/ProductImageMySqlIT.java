package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.service.ProductImageConflictException;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import com.ashenox.starter.catalog.product.image.storage.ImageStorageCleanupJob;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit MySQL 8.4 lifecycle test with fake S3 and an isolated database. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(ProductImageFlowTest.FakeStorageConfig.class)
class ProductImageMySqlIT {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Autowired ProductImageService service;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired ProductImageRepository images;
    @Autowired ImageStorageOperationRepository operations;
    @Autowired ImageStorageCleanupJob cleanup;
    @Autowired ProductImageFlowTest.FakeStorage storage;
    private Long productId;
    private Long categoryId;

    @AfterEach
    void cleanFixtures() {
        if (productId != null) {
            operations.deleteAll(operations.findAll().stream()
                    .filter(op -> op.getProduct().getId().equals(productId)).toList());
            images.deleteAll(images.findAll().stream()
                    .filter(image -> image.getProduct().getId().equals(productId)).toList());
            products.deleteById(productId);
        }
        if (categoryId != null) categories.deleteById(categoryId);
    }

    @Test
    void twoConcurrentReservationsForLastSlotPublishOnlyOne() throws Exception {
        createFixture();
        for (int i = 0; i < 4; i++) service.upload(productId, file());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> uploadAfterStart(start));
            var b = pool.submit(() -> uploadAfterStart(start));
            start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(service.list(productId)).hasSize(5);
        assertThat(service.list(productId).stream().filter(image -> image.primary()).count()).isEqualTo(1);
    }

    @Test
    void galleryLifecycleKeepsOnePrimaryAndEventuallyDeletesObjects() throws Exception {
        createFixture();
        var first = service.upload(productId, file());
        var second = service.upload(productId, file());
        var third = service.upload(productId, file());
        service.reorder(productId, List.of(third.id(), first.id(), second.id()));
        service.setPrimary(productId, second.id());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> changePrimary(start, first.id()));
            var b = pool.submit(() -> changePrimary(start, third.id()));
            start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.list(productId).stream().filter(image -> image.primary()).count()).isEqualTo(1);
        var principal = service.list(productId).stream().filter(image -> image.primary()).findFirst().orElseThrow();
        service.delete(productId, principal.id());
        assertThat(service.list(productId)).hasSize(2);
        assertThat(service.list(productId)).extracting(image -> image.position()).containsExactly(1, 2);
        assertThat(service.list(productId).stream().filter(image -> image.primary()).count()).isEqualTo(1);
        assertThat(storage.objects).containsKey(key(principal.thumbnailUrl()));
        cleanup.run();
        assertThat(storage.objects).doesNotContainKeys(key(principal.thumbnailUrl()), key(principal.detailUrl()));
    }

    private boolean changePrimary(CountDownLatch start, Long imageId) throws Exception {
        start.await();
        service.setPrimary(productId, imageId);
        return true;
    }

    private String key(String url) { return url.substring(url.indexOf("/products/") + 1); }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Concurrencia imagen " + suffix)
                .nombreNormalizado("concurrencia-imagen-" + suffix).icono("test").build());
        categoryId = category.getId();
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Concurrencia imagen " + suffix)
                .precioReferencia(BigDecimal.ONE).disponible(false).build());
        productId = product.getId();
    }

    private int uploadAfterStart(CountDownLatch start) throws Exception {
        start.await();
        try {
            service.upload(productId, file());
            return 1;
        } catch (ProductImageConflictException expected) {
            return 0;
        }
    }

    private MockMultipartFile file() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "race.png", "image/png", out.toByteArray());
    }
}
