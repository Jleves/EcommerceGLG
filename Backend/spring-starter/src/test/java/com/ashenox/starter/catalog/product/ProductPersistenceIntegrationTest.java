package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductPersistenceIntegrationTest {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private EntityManager entityManager;

    @Test
    void flywaySchemaAndJpaMappingPersistAndReloadProduct() {
        Category category = categories.saveAndFlush(Category.builder()
                .nombre("Pinturas").nombreNormalizado("pinturas").icono("paint").build());
        Product saved = products.saveAndFlush(Product.builder()
                .category(category)
                .nombre("Pintura blanca")
                .descripcion("Látex interior")
                .build());
        variants.saveAndFlush(ProductVariant.builder().product(saved)
                .precioReferencia(new BigDecimal("1234567890123.45")).disponible(false).activo(true).build());

        assertThat(saved.getId()).isNotNull();
        entityManager.clear();

        Product reloaded = products.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCategory().getId()).isEqualTo(category.getId());
        assertThat(reloaded.getNombre()).isEqualTo("Pintura blanca");
        assertThat(reloaded.getDescripcion()).isEqualTo("Látex interior");
        var variant = variants.findByProductId(saved.getId()).orElseThrow();
        assertThat(variant.getPrecioReferencia()).isEqualByComparingTo("1234567890123.45");
        assertThat(variant.isDisponible()).isFalse();
        assertThat(reloaded.isActivo()).isTrue();
        assertThat(reloaded.isDestacado()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
