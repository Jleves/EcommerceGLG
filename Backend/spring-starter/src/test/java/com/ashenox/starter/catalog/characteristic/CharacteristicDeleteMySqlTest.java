package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CharacteristicDeleteMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired CharacteristicService characteristics;
    @Autowired ProductService products;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void deleteRemovesOnlyTargetValuesAndPreservesProductsAndOtherValues() {
        Category category = category();
        long target = definition();
        long other = definition();
        long first = product(category, List.of(new ProductCharacteristicRequest(target, "Acme", "kg"),
                new ProductCharacteristicRequest(other, "Rojo", null)));
        long second = product(category, List.of(new ProductCharacteristicRequest(target, "Otra", "m")));
        products.deactivate(second);

        characteristics.delete(target);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM characteristics WHERE id = ?", Integer.class, target)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_characteristics WHERE characteristic_id = ?",
                Integer.class, target)).isZero();
        assertThat(products.getById(first).caracteristicas()).singleElement()
                .satisfies(value -> {
                    assertThat(value.caracteristicaId()).isEqualTo(other);
                    assertThat(value.valor()).isEqualTo("Rojo");
                });
        assertThat(products.getById(second).caracteristicas()).isEmpty();
        assertThat(products.getById(second).activo()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id IN (?, ?)", Integer.class,
                first, second)).isEqualTo(2);
        assertThat(characteristics.getById(other).cantidadProductos()).isEqualTo(1);

        long unused = definition();
        characteristics.delete(unused);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM characteristics WHERE id = ?", Integer.class, unused)).isZero();
    }

    @Test
    void failureDeletingDefinitionRollsBackRemovedValues() {
        Category category = category();
        long target = definition();
        long id = product(category, List.of(new ProductCharacteristicRequest(target, "Acme", "kg")));
        jdbc.execute("CREATE TRIGGER reject_characteristic_delete BEFORE DELETE ON characteristics "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced rollback'");
        try {
            assertThatThrownBy(() -> characteristics.delete(target)).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_characteristic_delete");
        }
        assertThat(characteristics.getById(target).cantidadProductos()).isEqualTo(1);
        assertThat(products.getById(id).caracteristicas()).singleElement()
                .satisfies(value -> {
                    assertThat(value.valor()).isEqualTo("Acme");
                    assertThat(value.unidad()).isEqualTo("kg");
                });
    }

    @Test
    void createCommittedFirstIsRemovedByWaitingDelete() throws Exception {
        Category category = category();
        long target = definition();
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch deleting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var creating = executor.submit(() -> transactions.execute(status -> {
                product(category, List.of(new ProductCharacteristicRequest(target, "Acme", "kg")));
                created.countDown();
                await(release);
                return null;
            }));
            assertThat(created.await(10, TimeUnit.SECONDS)).isTrue();
            var deletingTask = executor.submit(() -> {
                deleting.countDown();
                characteristics.delete(target);
            });
            assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deletingTask.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            creating.get(10, TimeUnit.SECONDS);
            deletingTask.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_characteristics WHERE characteristic_id = ?",
                Integer.class, target)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = ?", Integer.class,
                category.getId())).isEqualTo(1);
    }

    @Test
    void deleteCommittedFirstMakesWaitingCreateRejectMissingId() throws Exception {
        Category category = category();
        long target = definition();
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch creating = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletingTask = executor.submit(() -> transactions.execute(status -> {
                characteristics.delete(target);
                deleted.countDown();
                await(release);
                return null;
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var creatingTask = executor.submit(() -> {
                creating.countDown();
                product(category, List.of(new ProductCharacteristicRequest(target, "Acme", "kg")));
            });
            assertThat(creating.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creatingTask.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            deletingTask.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> creatingTask.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.ashenox.starter.shared.error.ResourceNotFoundException.class);
        } finally {
            release.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM characteristics WHERE id = ?", Integer.class, target)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = ?", Integer.class,
                category.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_characteristics WHERE characteristic_id = ?",
                Integer.class, target)).isZero();
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").build());
    }

    private long definition() {
        return characteristics.create(new CreateCharacteristicRequest("Marca " + UUID.randomUUID())).id();
    }

    private long product(Category category, List<ProductCharacteristicRequest> values) {
        return products.create(new CreateProductRequest("Arena", category.getId(), null,
                new BigDecimal("10.00"), true, values)).id();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
