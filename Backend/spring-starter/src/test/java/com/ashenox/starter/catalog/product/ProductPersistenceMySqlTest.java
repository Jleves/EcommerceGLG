package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductPersistenceMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void mysqlPersistsExactDecimalAndAuditFields() {
        Category category = category();
        Product saved = products.saveAndFlush(Product.builder()
                .category(category).nombre("Arena")
                .precioReferencia(new BigDecimal("1234567890123.45"))
                .disponible(true).build());

        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT precio_referencia FROM products WHERE id = ?", BigDecimal.class, saved.getId());
        assertThat(storedPrice).isEqualByComparingTo("1234567890123.45");
        assertThat(products.findById(saved.getId()).orElseThrow().getPrecioReferencia())
                .isEqualByComparingTo("1234567890123.45");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ? AND created_at IS NOT NULL AND updated_at IS NOT NULL",
                Integer.class, saved.getId())).isEqualTo(1);
    }

    @Test
    void mysqlRejectsMissingCategoryAndNonPositivePrice() {
        Category category = category();
        assertThatThrownBy(() -> insert(0L, new BigDecimal("1.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertCheckRejects(category.getId(), new BigDecimal("0.00"));
        assertCheckRejects(category.getId(), new BigDecimal("-1.00"));
    }

    @Test
    void mysqlForeignKeyPreventsDeletingCategoryWithProduct() {
        Category category = category();
        products.saveAndFlush(Product.builder().category(category).nombre("Cemento")
                .precioReferencia(new BigDecimal("10.00")).disponible(true).build());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = ?", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder()
                .nombre("Categoría " + suffix).nombreNormalizado("categoria-" + suffix)
                .icono("icon").build());
    }

    private void insert(Long categoryId, BigDecimal price) {
        jdbc.update("""
                INSERT INTO products (category_id, nombre, precio_referencia, disponible, created_at, updated_at)
                VALUES (?, 'Prueba', ?, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, categoryId, price);
    }

    private void assertCheckRejects(Long categoryId, BigDecimal price) {
        assertThatThrownBy(() -> insert(categoryId, price))
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure.getCause()).getErrorCode())
                        .isEqualTo(3819));
    }
}
