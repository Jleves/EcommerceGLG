package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductCharacteristicAtomicityMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductRepository products;
    @Autowired ProductService service;
    @MockitoBean ProductCharacteristicRepository values;

    @Test
    void associationFailureRollsBackInsertedProductInMySql() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
        Characteristic definition = characteristics.saveAndFlush(Characteristic.builder().nombre("Marca " + suffix)
                .nombreNormalizado("marca-" + suffix).build());
        long before = products.count();
        when(values.saveAllAndFlush(any())).thenThrow(new IllegalStateException("Fallo al guardar asociación"));

        assertThatThrownBy(() -> service.create(new CreateProductRequest("Arena", category.getId(), null,
                new BigDecimal("10.00"), true,
                List.of(new ProductCharacteristicRequest(definition.getId(), "Acme", null)))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isEqualTo(before);
    }
}
