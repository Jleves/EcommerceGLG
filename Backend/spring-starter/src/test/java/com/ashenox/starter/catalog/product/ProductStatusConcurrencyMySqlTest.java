package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductStatusConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired private CategoryRepository categories;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void reactivateWaitsForDeactivationOfSameProduct() throws Exception {
        String unique = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder()
                .nombre("Materiales " + unique).nombreNormalizado("materiales-" + unique)
                .icono("icon").activo(true).build());
        long productId = productService.create(new CreateProductRequest("Arena", category.getId(),
                "Para obra", new BigDecimal("125.50"), false)).id();
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(productService.deactivate(productId).activo()).isFalse();
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThat(productService.reactivate(productId).activo()).isTrue();
                }));
                await(started);
                awaitDatabaseWait();
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }

        var saved = products.findById(productId).orElseThrow();
        assertThat(saved.isActivo()).isTrue();
        assertThat(saved.getCategory().getId()).isEqualTo(category.getId());
        assertThat(saved.getNombre()).isEqualTo("Arena");
        assertThat(saved.getDescripcion()).isEqualTo("Para obra");
        assertThat(productService.getById(saved.getId()).precioReferencia()).isEqualByComparingTo("125.50");
        assertThat(productService.getById(saved.getId()).disponible()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitDatabaseWait() {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     select count(*) from performance_schema.data_lock_waits w
                     join performance_schema.data_locks l on l.engine_lock_id = w.requesting_engine_lock_id
                     where l.object_schema = ? and l.object_name = 'products'
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) return;
                }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("No se observó espera por bloqueo sobre products en MySQL");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
