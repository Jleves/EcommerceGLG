package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.service.ProductCategoryInactiveException;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.catalog.subcategory.service.SubcategoryService;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductSubcategoryConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired CategoryService categoryService;
    @Autowired SubcategoryRepository subcategories;
    @Autowired SubcategoryService subcategoryService;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createAssignmentCommitsFirstThenDeleteConflicts() throws Exception {
        Category category = category();
        Subcategory subcategory = subcategory(category);
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);
        var productId = new AtomicReference<Long>();

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                productId.set(productService.create(request(category.getId(), subcategory.getId())).id());
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> subcategoryService.delete(subcategory.getId()))
                            .isInstanceOf(com.ashenox.starter.catalog.subcategory.service.SubcategoryConflictException.class);
                }));
                await(started);
                awaitDatabaseWait("subcategories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(subcategories.existsById(subcategory.getId())).isTrue();
        assertThat(jdbc.queryForObject("select subcategory_id from products where id = ?", Long.class,
                productId.get())).isEqualTo(subcategory.getId());
        assertThat(products.findById(productId.get()).orElseThrow().getCategory().getId())
                .isEqualTo(category.getId());
    }

    @Test
    void deleteFirstMakesWaitingCreateRejectWithoutDanglingReference() throws Exception {
        Category category = category();
        Subcategory subcategory = subcategory(category);
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                subcategoryService.delete(subcategory.getId());
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> productService.create(request(category.getId(), subcategory.getId())))
                            .isInstanceOf(ResourceNotFoundException.class);
                }));
                await(started);
                awaitDatabaseWait("subcategories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from products where subcategory_id = ?", Long.class,
                subcategory.getId())).isZero();
    }

    @Test
    void updateAssignmentAndDeleteUseSameLockOrder() throws Exception {
        Category category = category();
        Subcategory subcategory = subcategory(category);
        long id = productService.create(request(category.getId(), null)).id();
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);
        UpdateProductRequest update = new UpdateProductRequest("Actualizado", category.getId(), null,
                new BigDecimal("12.50"), true);
        update.setSubcategoriaId(subcategory.getId());

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                productService.update(id, update);
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> subcategoryService.delete(subcategory.getId()))
                            .isInstanceOf(com.ashenox.starter.catalog.subcategory.service.SubcategoryConflictException.class);
                }));
                await(started);
                awaitDatabaseWait("subcategories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select subcategory_id from products where id = ?", Long.class, id))
                .isEqualTo(subcategory.getId());
        assertThat(products.findById(id).orElseThrow().getNombre()).isEqualTo("Actualizado");
    }

    @Test
    void childCreationFirstMakesParentDeletionConflict() throws Exception {
        Category category = category();
        Subcategory parent = subcategory(category);
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                subcategoryService.create(new com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest(
                        category.getId(), "Hijo", null, parent.getId()));
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> subcategoryService.delete(parent.getId()))
                            .isInstanceOf(com.ashenox.starter.catalog.subcategory.service.SubcategoryConflictException.class);
                }));
                await(started);
                awaitDatabaseWait("subcategories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(subcategories.existsById(parent.getId())).isTrue();
        assertThat(subcategories.countByParentSubcategoryId(parent.getId())).isEqualTo(1);
    }

    @Test
    void categoryChangeFirstMakesCategoryDeletionConflict() throws Exception {
        Category source = category();
        Category destination = category();
        long id = productService.create(request(source.getId(), null)).id();
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);
        UpdateProductRequest update = new UpdateProductRequest("Actualizado", destination.getId(), null,
                new BigDecimal("12.50"), true);
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                productService.update(id, update);
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> categoryService.delete(destination.getId()))
                            .isInstanceOf(com.ashenox.starter.catalog.category.service.CategoryConflictException.class);
                }));
                await(started);
                awaitDatabaseWait("categories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(categories.existsById(destination.getId())).isTrue();
        assertThat(products.findById(id).orElseThrow().getCategory().getId()).isEqualTo(destination.getId());
    }

    @Test
    void deactivationFirstMakesWaitingUpdateAssignmentReject() throws Exception {
        Category category = category();
        Subcategory subcategory = subcategory(category);
        long id = productService.create(request(category.getId(), null)).id();
        UpdateProductRequest update = new UpdateProductRequest("Actualizado", category.getId(), null,
                new BigDecimal("12.50"), true);
        update.setSubcategoriaId(subcategory.getId());
        var waiting = new AtomicReference<Future<?>>();
        var started = new CountDownLatch(1);

        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                categoryService.deactivate(category.getId());
                waiting.set(pool.submit(() -> {
                    started.countDown();
                    assertThatThrownBy(() -> productService.update(id, update))
                            .isInstanceOf(ProductCategoryInactiveException.class);
                }));
                await(started);
                awaitDatabaseWait("categories");
                assertThat(waiting.get()).isNotDone();
            });
            waiting.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select subcategory_id from products where id = ?", Long.class, id))
                .isNull();
        assertThat(products.findById(id).orElseThrow().getNombre()).isEqualTo("Producto");
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Category " + suffix)
                .nombreNormalizado("category-" + suffix).icono("icon").activo(true).build());
    }

    private Subcategory subcategory(Category category) {
        String suffix = UUID.randomUUID().toString();
        return subcategories.saveAndFlush(Subcategory.builder().category(category)
                .nombre("Sub " + suffix).nombreNormalizado("sub-" + suffix).build());
    }

    private CreateProductRequest request(Long categoryId, Long subcategoryId) {
        return new CreateProductRequest("Producto", categoryId, null, new BigDecimal("12.50"),
                true, null, subcategoryId);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitDatabaseWait(String table) {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     select count(*) from performance_schema.data_lock_waits w
                     join performance_schema.data_locks l on l.engine_lock_id = w.requesting_engine_lock_id
                     where l.object_schema = ? and l.object_name = ?
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) return;
                }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("No se observó espera por bloqueo sobre " + table);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
