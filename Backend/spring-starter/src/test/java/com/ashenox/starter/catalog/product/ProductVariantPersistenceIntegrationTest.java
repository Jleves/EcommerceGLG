package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.repository.VariantCharacteristicRepository;
import jakarta.persistence.EntityManager;
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
@Transactional
class ProductVariantPersistenceIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductService products;
    @Autowired ProductVariantRepository variants;
    @Autowired VariantCharacteristicRepository variantCharacteristics;
    @Autowired EntityManager entityManager;

    @Test
    void jpaPersistsInitialVariantAndItsOptionalSingleCharacteristic() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Materiales " + suffix)
                .nombreNormalizado("materiales-" + suffix).icono("icon").build());
        Characteristic characteristic = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Color " + suffix).nombreNormalizado("color-" + suffix).build());
        var product = products.create(new CreateProductRequest("Pintura", category.getId(), null,
                new BigDecimal("15.25"), true));
        var variant = variants.findById(product.varianteInicialId()).orElseThrow();
        assertThat(variant.isActivo()).isTrue();
        assertThat(variant.getPrecioReferencia()).isEqualByComparingTo("15.25");
        assertThat(variant.isDisponible()).isTrue();
        assertThat(variant.getSku()).isNull();

        variantCharacteristics.saveAndFlush(VariantCharacteristic.builder().variant(variant)
                .characteristic(characteristic).valor("Rojo").valorNormalizado("rojo")
                .unidad("L").unidadNormalizada("l").build());
        entityManager.clear();

        var reloaded = variantCharacteristics.findById(variant.getId()).orElseThrow();
        assertThat(reloaded.getVariant().getProduct().getId()).isEqualTo(product.id());
        assertThat(reloaded.getCharacteristic().getId()).isEqualTo(characteristic.getId());
        assertThat(reloaded.getValor()).isEqualTo("Rojo");
        assertThat(reloaded.getUnidadNormalizada()).isEqualTo("l");
        assertThat(variantCharacteristics.findByVariantProductId(product.id())).hasSize(1);
    }
}
