package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.service.ProductImageConflictException;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit MySQL concurrency test with fake S3; invoke only against the local staging database. */
@SpringBootTest
@Import(ProductImageFlowTest.FakeStorageConfig.class)
class ProductImageMySqlIT {
    @Autowired ProductImageService service;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired ProductImageRepository images;
    @Autowired ImageStorageOperationRepository operations;
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
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Concurrencia imagen " + suffix)
                .nombreNormalizado("concurrencia-imagen-" + suffix).icono("test").build());
        categoryId = category.getId();
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Concurrencia imagen " + suffix)
                .precioReferencia(BigDecimal.ONE).disponible(false).build());
        productId = product.getId();
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
