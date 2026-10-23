package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
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
    @Autowired private ProductVariantRepository variants;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void mysqlPersistsExactDecimalAndAuditFields() {
        Category category = category();
        Product saved = products.saveAndFlush(Product.builder()
                .category(category).nombre("Arena").build());
        ProductVariant variant = variants.saveAndFlush(ProductVariant.builder().product(saved)
                .precioReferencia(new BigDecimal("1234567890123.45")).disponible(true).activo(true).build());

        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT precio_referencia FROM product_variants WHERE id = ?", BigDecimal.class, variant.getId());
        assertThat(storedPrice).isEqualByComparingTo("1234567890123.45");
        assertThat(variants.findById(variant.getId()).orElseThrow().getPrecioReferencia())
                .isEqualByComparingTo("1234567890123.45");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ? AND created_at IS NOT NULL AND updated_at IS NOT NULL",
                Integer.class, saved.getId())).isEqualTo(1);
    }

    @Test
    void mysqlRejectsMissingCategoryAndNonPositivePrice() {
        Category category = category();
        assertThatThrownBy(() -> insert(0L))
                .isInstanceOf(DataIntegrityViolationException.class);
        Product saved = products.saveAndFlush(Product.builder().category(category).nombre("Prueba").build());
        assertCheckRejects(saved.getId(), new BigDecimal("0.00"));
        assertCheckRejects(saved.getId(), new BigDecimal("-1.00"));
    }

    @Test
    void mysqlForeignKeyPreventsDeletingCategoryWithProduct() {
        Category category = category();
        products.saveAndFlush(Product.builder().category(category).nombre("Cemento")
                .build());

        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = ?", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder()
                .nombre("Categoría " + suffix).nombreNormalizado("categoria-" + suffix)
                .icono("icon").build());
    }

    private void insert(Long categoryId) {
        jdbc.update("""
                INSERT INTO products (category_id, nombre, created_at, updated_at)
                VALUES (?, 'Prueba', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, categoryId);
    }

    private void assertCheckRejects(Long productId, BigDecimal price) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_variants
                    (product_id, precio_referencia, disponible, activo, created_at, updated_at)
                VALUES (?, ?, TRUE, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, productId, price))
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure.getCause()).getErrorCode())
                        .isEqualTo(3819));
    }
}
