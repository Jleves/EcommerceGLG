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
class ProductVariantPersistenceMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void mysqlStoresExactPriceAndAllowsMultipleNullSkusButRejectsDuplicateNormalizedSku() {
        Long productId = productId();
        insert(productId, "SKU-1", "sku-1", new BigDecimal("1234567890123.45"));
        insert(productId, null, null, new BigDecimal("1.00"));
        insert(productId, null, null, new BigDecimal("2.00"));
        assertThat(jdbc.queryForObject("""
                SELECT precio_referencia FROM product_variants WHERE sku_normalizado = 'sku-1'
                """, BigDecimal.class)).isEqualByComparingTo("1234567890123.45");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_variants WHERE product_id = ? AND sku_normalizado IS NULL
                """, Integer.class, productId)).isEqualTo(2);
        assertThatThrownBy(() -> insert(productId, "sku-1", "sku-1", new BigDecimal("3.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mysqlRejectsNonPositivePriceAndMissingProduct() {
        Long productId = productId();
        assertCheckRejects(productId, BigDecimal.ZERO);
        assertCheckRejects(productId, new BigDecimal("-1.00"));
        assertThatThrownBy(() -> insert(Long.MAX_VALUE, null, null, BigDecimal.ONE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertCheckRejects(Long productId, BigDecimal price) {
        assertThatThrownBy(() -> insert(productId, null, null, price))
                .hasCauseInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure.getCause()).getErrorCode())
                        .isEqualTo(3819));
    }

    private Long productId() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").build());
        return products.saveAndFlush(Product.builder().category(category).nombre("Producto " + suffix)
                .build()).getId();
    }

    private void insert(Long productId, String sku, String normalizedSku, BigDecimal price) {
        jdbc.update("""
                INSERT INTO product_variants
                    (product_id, sku, sku_normalizado, precio_referencia, disponible, activo, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, productId, sku, normalizedSku, price);
    }
}
