package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CharacteristicUsageMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CharacteristicService characteristics;
    @Autowired ProductService products;
    @Autowired CategoryRepository categories;

    @Test
    void usageCountsAndProductDetailWorkOnMySql() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").build());
        long characteristicId = characteristics.create(new CreateCharacteristicRequest("Marca " + suffix)).id();
        long productId = products.create(new CreateProductRequest("Arena", category.getId(), null,
                new BigDecimal("10.00"), true,
                List.of(new ProductCharacteristicRequest(characteristicId, "Acme", "kg")))).id();
        products.deactivate(productId);

        assertThat(characteristics.getById(characteristicId).cantidadProductos()).isEqualTo(1);
        assertThat(characteristics.list()).filteredOn(item -> item.id().equals(characteristicId))
                .singleElement().extracting(item -> item.cantidadProductos()).isEqualTo(1L);
        assertThat(products.getById(productId).caracteristicas().getFirst().valor()).isEqualTo("Acme");
        assertThat(products.getById(productId).caracteristicas().getFirst().unidad()).isEqualTo("kg");
    }
}
