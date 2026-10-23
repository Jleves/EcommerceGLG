package com.ashenox.starter.catalog.subcategory;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SubcategoryPersistenceMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired private CategoryRepository categories;
    @Autowired private SubcategoryRepository subcategories;
    @Autowired private ProductRepository products;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void uniqueNameIsScopedToParentAndForeignKeysRestrictDanglingLinks() {
        Category first = category();
        Category second = category();
        Subcategory one = subcategories.saveAndFlush(subcategory(first, "Chapas", "chapas"));
        Subcategory two = subcategories.saveAndFlush(subcategory(second, "Chapas", "chapas"));
        assertThat(one.getId()).isNotEqualTo(two.getId());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO subcategories (category_id, nombre, nombre_normalizado, created_at, updated_at)
                VALUES (?, 'CHAPAS', 'chapas', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, first.getId())).isInstanceOf(DataIntegrityViolationException.class);

        Product product = products.saveAndFlush(Product.builder().category(first).subcategory(one)
                .nombre("Chapa").build());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM subcategories WHERE id = ?", one.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT category_id FROM products WHERE id = ?", Long.class, product.getId()))
                .isEqualTo(first.getId());
    }

    @Test
    void migrationLeavesExistingStyleProductWithoutSubcategory() {
        Category parent = category();
        Product product = products.saveAndFlush(Product.builder().category(parent)
                .nombre("Sin subcategoría")
                .build());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM products
                WHERE id = ? AND category_id = ? AND subcategory_id IS NULL
                """, Integer.class, product.getId(), parent.getId())).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getSubcategory()).isNull();
    }

    @Test
    void migrationCreatesExpectedIndexesAndConstraints() {
        assertThat(jdbc.queryForList("""
                SELECT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'subcategories'
                """, String.class)).contains("uk_subcategories_category_name", "idx_subcategories_category_id");
        assertThat(jdbc.queryForList("""
                SELECT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'products'
                """, String.class)).contains("idx_products_subcategory_id");
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder()
                .nombre("Categoría " + suffix).nombreNormalizado("categoria-" + suffix)
                .icono("icon").build());
    }

    private Subcategory subcategory(Category parent, String name, String normalized) {
        return Subcategory.builder().category(parent).nombre(name).nombreNormalizado(normalized).build();
    }
}
