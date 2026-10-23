package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.variant.dto.CreateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.UpdateVariantRequest;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class ProductVariantConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired CharacteristicService characteristics;
    @Autowired ProductService products;
    @Autowired ProductVariantService variants;
    @Autowired TransactionTemplate transactions;

    @Test
    void concurrentSameValueCreatesOnlyOneVariant() throws Exception {
        Long category = category();
        Long color = characteristics.create(new CreateCharacteristicRequest("Color " + UUID.randomUUID())).id();
        Long product = product(category);
        variants.update(product, products.getById(product).varianteInicialId(),
                new UpdateVariantRequest(null, BigDecimal.TEN, true, color, "Rojo", null));
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CreateVariantRequest candidate = new CreateVariantRequest(null, new BigDecimal("12"), true,
                color, "Blanco", null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status -> {
                variants.create(product, candidate);
                inserted.countDown();
                await(release);
                return null;
            }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> variants.create(product, candidate));
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RuntimeException.class);
        } finally {
            release.countDown();
        }
        assertThat(variants.list(product)).hasSize(2);
    }

    @Test
    void concurrentSkuOnDifferentProductsUsesUniqueIndex() throws Exception {
        Long category = category();
        Long firstProduct = product(category);
        Long secondProduct = product(category);
        Long firstVariant = products.getById(firstProduct).varianteInicialId();
        Long secondVariant = products.getById(secondProduct).varianteInicialId();
        String sku = "sku-" + UUID.randomUUID();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status -> {
                variants.update(firstProduct, firstVariant,
                        new UpdateVariantRequest(sku, BigDecimal.TEN, true, null, null, null));
                written.countDown();
                await(release);
                return null;
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> variants.update(secondProduct, secondVariant,
                    new UpdateVariantRequest(sku.toUpperCase(), BigDecimal.TEN, true, null, null, null)));
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RuntimeException.class);
        } finally {
            release.countDown();
        }
        assertThat(variants.list(firstProduct).getFirst().sku()).isEqualTo(sku);
        assertThat(variants.list(secondProduct).getFirst().sku()).isNull();
    }

    private Long category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").activo(true).build()).getId();
    }

    private Long product(Long category) {
        return products.create(new CreateProductRequest("Producto " + UUID.randomUUID(), category,
                null, BigDecimal.TEN, true)).id();
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
