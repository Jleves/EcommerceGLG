package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CharacteristicPersistenceMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CharacteristicRepository characteristics;

    @Test
    void v7AndConstraintsProtectDefinitionsAssociationsAndProducts() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = 1", Integer.class)).isEqualTo(1);
        String suffix = UUID.randomUUID().toString();
        Characteristic definition = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Marca " + suffix).nombreNormalizado("marca-" + suffix).build());
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Arena")
                .build());

        assertThatThrownBy(() -> jdbc.update("INSERT INTO characteristics (nombre,nombre_normalizado,created_at,updated_at) VALUES (?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))", "Otra", "marca-" + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(product.getId(), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(Long.MAX_VALUE, definition.getId())).isInstanceOf(DataIntegrityViolationException.class);
        insert(product.getId(), definition.getId());
        assertThatThrownBy(() -> insert(product.getId(), definition.getId())).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM product_characteristics WHERE product_id = ? AND characteristic_id = ?", product.getId(), definition.getId());
        assertThat(products.findById(product.getId())).isPresent();
    }


    private void insert(Long productId, Long characteristicId) {
        jdbc.update("INSERT INTO product_characteristics (product_id,characteristic_id,valor) VALUES (?,?,'Acme')", productId, characteristicId);
    }
}
