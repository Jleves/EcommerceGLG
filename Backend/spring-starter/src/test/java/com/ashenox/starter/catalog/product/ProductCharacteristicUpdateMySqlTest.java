package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductCharacteristicUpdateMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired ProductService products;
    @Autowired CharacteristicService characteristics;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void persistenceFailureRollsBackBasicFieldsAndAssociationChanges() {
        Category category = category();
        long retained = definition();
        long added = definition();
        long product = product(category, List.of(item(retained, "Original")));
        jdbc.execute("CREATE TRIGGER reject_value_insert BEFORE INSERT ON product_characteristics "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced rollback'");
        try {
            assertThatThrownBy(() -> products.update(product, update(category, "Cambiado",
                    List.of(item(added, "Nuevo"))))).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_value_insert");
        }
        assertThat(products.getById(product).nombre()).isEqualTo("Original");
        assertThat(products.getById(product).caracteristicas()).singleElement()
                .satisfies(value -> {
                    assertThat(value.caracteristicaId()).isEqualTo(retained);
                    assertThat(value.valor()).isEqualTo("Original");
                });
    }

    @Test
    void editCommittedFirstIsRemovedByWaitingDelete() throws Exception {
        Category category = category();
        long target = definition();
        long product = product(category, List.of());
        CountDownLatch edited = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var editing = executor.submit(() -> transactions.execute(status -> {
                products.update(product, update(category, "Editado", List.of(item(target, "Nuevo"))));
                edited.countDown();
                await(release);
                return null;
            }));
            assertThat(edited.await(10, TimeUnit.SECONDS)).isTrue();
            var deleting = executor.submit(() -> characteristics.delete(target));
            assertThatThrownBy(() -> deleting.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            editing.get(10, TimeUnit.SECONDS);
            deleting.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(products.getById(product).caracteristicas()).isEmpty();
        assertThat(products.getById(product).nombre()).isEqualTo("Editado");
    }

    @Test
    void deleteCommittedFirstMakesWaitingEditRejectMissingDefinition() throws Exception {
        Category category = category();
        long target = definition();
        long product = product(category, List.of());
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deleting = executor.submit(() -> transactions.execute(status -> {
                characteristics.delete(target);
                deleted.countDown();
                await(release);
                return null;
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var editing = executor.submit(() -> products.update(product,
                    update(category, "Cambiado", List.of(item(target, "Nuevo")))));
            assertThatThrownBy(() -> editing.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            deleting.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> editing.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.ashenox.starter.shared.error.ResourceNotFoundException.class);
        } finally {
            release.countDown();
        }
        assertThat(products.getById(product).nombre()).isEqualTo("Original");
        assertThat(products.getById(product).caracteristicas()).isEmpty();
    }

    @Test
    void twoEditsOfSameProductSerialize() throws Exception {
        Category category = category();
        long target = definition();
        long product = product(category, List.of(item(target, "Original")));
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(status -> {
                products.update(product, update(category, "Primero", List.of(item(target, "Uno"))));
                firstUpdated.countDown();
                await(release);
                return null;
            }));
            assertThat(firstUpdated.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> products.update(product,
                    update(category, "Segundo", List.of(item(target, "Dos")))));
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertThat(products.getById(product).nombre()).isEqualTo("Segundo");
        assertThat(products.getById(product).caracteristicas().getFirst().valor()).isEqualTo("Dos");
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
    }

    private long definition() {
        return characteristics.create(new CreateCharacteristicRequest("Marca " + UUID.randomUUID())).id();
    }

    private long product(Category category, List<ProductCharacteristicRequest> items) {
        return products.create(new CreateProductRequest("Original", category.getId(), null,
                new BigDecimal("10"), true, items)).id();
    }

    private ProductCharacteristicRequest item(long id, String value) {
        return new ProductCharacteristicRequest(id, value, "kg");
    }

    private UpdateProductRequest update(Category category, String name, List<ProductCharacteristicRequest> items) {
        return new UpdateProductRequest(name, category.getId(), null, new BigDecimal("20"), false, items);
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
