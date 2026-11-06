package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.offer.service.OfferConflictException;
import com.ashenox.starter.catalog.product.offer.service.OfferService;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OfferConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired OfferRepository offers;
    @Autowired OfferService service;
    @Autowired ProductService productService;
    @Autowired TransactionTemplate transactions;

    @Test
    void simultaneousOffersForOneVariantLeaveOneRow() throws Exception {
        ProductVariant variant = variant();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status -> {
                service.create(variant.getId(), new BigDecimal("75.00"));
                inserted.countDown();
                await(release);
                return null;
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.create(variant.getId(), new BigDecimal("70.00")));
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
        } finally {
            release.countDown();
        }
        assertThat(offers.findByVariantId(variant.getId()).orElseThrow().getPrecioOferta()).isEqualByComparingTo("75.00");
        assertThat(offers.countByVariantId(variant.getId())).isEqualTo(1);
    }

    @Test
    void basePriceEditWaitsForOfferAndCannotInvalidateIt() throws Exception {
        ProductVariant variant = variant();
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status -> {
                service.create(variant.getId(), new BigDecimal("75.00"));
                inserted.countDown();
                await(release);
                return null;
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> productService.update(variant.getProduct().getId(),
                    new UpdateProductRequest("No guardar", variant.getProduct().getCategory().getId(),
                            null, new BigDecimal("70.00"), true)));
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(OfferConflictException.class);
        } finally {
            release.countDown();
        }
        assertThat(variants.findById(variant.getId()).orElseThrow().getPrecioReferencia()).isEqualByComparingTo("100.00");
        assertThat(offers.findByVariantId(variant.getId()).orElseThrow().getPrecioOferta()).isEqualByComparingTo("75.00");
    }

    private ProductVariant variant() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("brick").activo(true).build());
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Producto " + suffix)
                .activo(true).build());
        return variants.saveAndFlush(ProductVariant.builder().product(product)
                .precioReferencia(new BigDecimal("100.00")).activo(true).disponible(true).build());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout concurrente");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
