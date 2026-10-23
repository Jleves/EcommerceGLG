package com.ashenox.starter.catalog.subcategory;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.catalog.subcategory.service.SubcategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SubcategoryDeleteMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired SubcategoryService service;
    @Autowired CategoryRepository categories;
    @Autowired SubcategoryRepository subcategories;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;

    @Test
    void deletesEmptySubcategoryAndFreesItsName() {
        Category parent = category();
        long id = service.create(new CreateSubcategoryRequest(parent.getId(), "Chapas", null)).id();
        service.delete(id);
        assertThat(subcategories.existsById(id)).isFalse();
        assertThat(service.create(new CreateSubcategoryRequest(parent.getId(), "CHAPAS", null)).id())
                .isNotEqualTo(id);
    }

    @Test
    void unlinksAllProductsAndTouchesOnlyTheirTimestamps() {
        Category parent = category();
        Category otherParent = category();
        long id = service.create(new CreateSubcategoryRequest(parent.getId(), "Chapas", null)).id();
        Subcategory subcategory = subcategories.findById(id).orElseThrow();
        Product first = product(parent, subcategory);
        Product second = product(parent, subcategory);
        Product other = product(otherParent, null);
        LocalDateTime old = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbc.update("UPDATE products SET updated_at = ? WHERE id IN (?, ?, ?)",
                Timestamp.valueOf(old), first.getId(), second.getId(), other.getId());

        assertThat(service.getById(id).productosAsociados()).isEqualTo(2);
        service.delete(id);

        assertThat(subcategories.existsById(id)).isFalse();
        for (Product linked : new Product[]{first, second}) {
            assertThat(jdbc.queryForObject("SELECT category_id FROM products WHERE id = ?", Long.class, linked.getId()))
                    .isEqualTo(parent.getId());
            assertThat(jdbc.queryForObject("SELECT subcategory_id FROM products WHERE id = ?", Long.class, linked.getId()))
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT updated_at FROM products WHERE id = ?", Timestamp.class,
                    linked.getId()).toLocalDateTime()).isAfter(old);
        }
        assertThat(jdbc.queryForObject("SELECT updated_at FROM products WHERE id = ?", Timestamp.class,
                other.getId()).toLocalDateTime()).isEqualTo(old);
        assertThat(products.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void databaseFailureAfterUnlinkRollsBackEverything() {
        Category parent = category();
        long id = service.create(new CreateSubcategoryRequest(parent.getId(), "Chapas", null)).id();
        Product product = product(parent, subcategories.findById(id).orElseThrow());
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS subcategory_delete_guard (
                    id BIGINT PRIMARY KEY, subcategory_id BIGINT NOT NULL,
                    CONSTRAINT fk_delete_guard FOREIGN KEY (subcategory_id) REFERENCES subcategories(id))
                """);
        jdbc.update("INSERT INTO subcategory_delete_guard (id, subcategory_id) VALUES (?, ?)", id, id);
        try {
            assertThatThrownBy(() -> service.delete(id)).isInstanceOf(Exception.class);
            assertThat(subcategories.existsById(id)).isTrue();
            assertThat(jdbc.queryForObject("SELECT subcategory_id FROM products WHERE id = ?", Long.class,
                    product.getId())).isEqualTo(id);
        } finally {
            jdbc.update("DELETE FROM subcategory_delete_guard WHERE id = ?", id);
        }
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Category " + suffix)
                .nombreNormalizado("category-" + suffix).icono("icon").build());
    }

    private Product product(Category category, Subcategory subcategory) {
        return products.saveAndFlush(Product.builder().category(category).subcategory(subcategory)
                .nombre("Product " + UUID.randomUUID())
                .build());
    }
}
