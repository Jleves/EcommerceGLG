package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.*;
import com.ashenox.starter.catalog.characteristic.repository.*;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CharacteristicPersistenceIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductCharacteristicRepository values;

    @Test
    @Transactional
    void flywaySchemaAndJpaPersistDefinitionAndProductValue() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Arena")
                .precioReferencia(new BigDecimal("10.00")).disponible(true).build());
        Characteristic definition = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Marca " + suffix).nombreNormalizado("marca-" + suffix).build());
        var id = new ProductCharacteristicId(product.getId(), definition.getId());
        values.saveAndFlush(ProductCharacteristic.builder().id(id).product(product)
                .characteristic(definition).valor("Acme").build());

        var stored = values.findById(id).orElseThrow();
        assertThat(stored.getValor()).isEqualTo("Acme");
        assertThat(stored.getCharacteristic().getNombre()).isEqualTo("Marca " + suffix);
        assertThat(stored.getUnidad()).isNull();
        assertThat(products.findById(product.getId())).isPresent();
    }
}
