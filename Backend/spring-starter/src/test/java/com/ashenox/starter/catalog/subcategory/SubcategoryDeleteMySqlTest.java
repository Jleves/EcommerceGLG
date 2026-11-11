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
    void rejectsDeletionAndPreservesProducts() {
        Category parent = category();
        long id = service.create(new CreateSubcategoryRequest(parent.getId(), "Chapas", null)).id();
        Product product = product(parent, subcategories.findById(id).orElseThrow());
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(com.ashenox.starter.catalog.subcategory.service.SubcategoryConflictException.class);
        assertThat(subcategories.existsById(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT subcategory_id FROM products WHERE id = ?", Long.class,
                product.getId())).isEqualTo(id);
    }

    @Test
    void rejectsDeletionWhenChildrenAndProductsExist() {
        Category category = category();
        long id = service.create(new CreateSubcategoryRequest(category.getId(), "Padre", null)).id();
        long child = service.create(new CreateSubcategoryRequest(category.getId(), "Hijo", null, id)).id();
        Product linked = product(category, subcategories.findById(id).orElseThrow());
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(com.ashenox.starter.catalog.subcategory.service.SubcategoryConflictException.class)
                .hasMessageContaining("hijas").hasMessageContaining("productos");
        assertThat(subcategories.existsById(child)).isTrue();
        assertThat(jdbc.queryForObject("SELECT subcategory_id FROM products WHERE id = ?", Long.class,
                linked.getId())).isEqualTo(id);
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
