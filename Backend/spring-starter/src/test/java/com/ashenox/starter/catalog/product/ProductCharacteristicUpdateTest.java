package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductCharacteristicUpdateTest {
    @Autowired ProductService products;
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductCharacteristicRepository values;

    @AfterEach
    void clearAssociations() {
        values.deleteAll();
    }

    @Test
    void omissionPreservesAndListReplacesWithFinalValues() {
        Category category = category();
        long retained = definition();
        long removed = definition();
        long added = definition();
        long product = products.create(new CreateProductRequest("Inicial", category.getId(), null,
                new BigDecimal("10"), true, List.of(item(retained, "Viejo", "kg"),
                item(removed, "Quitar", null)))).id();

        assertThat(products.update(product, update(category, "Básico")).caracteristicas()).hasSize(2);
        var updated = products.update(product, update(category, "Final", List.of(
                item(retained, " Nuevo ", "  "), item(added, " Agregado ", " cm "))));
        assertThat(updated.nombre()).isEqualTo("Final");
        assertThat(updated.caracteristicas()).extracting(value -> value.caracteristicaId())
                .containsExactly(retained, added);
        assertThat(updated.caracteristicas().getFirst().valor()).isEqualTo("Nuevo");
        assertThat(updated.caracteristicas().getFirst().unidad()).isNull();
        assertThat(updated.caracteristicas().getLast().unidad()).isEqualTo("cm");
        assertThat(products.getById(product).caracteristicas()).isEqualTo(updated.caracteristicas());
        assertThat(products.update(product, update(category, "Vacío", List.of())).caracteristicas()).isEmpty();
        assertThat(products.getById(product).caracteristicas()).isEmpty();
    }

    @Test
    void invalidReplacementRollsBackBasicFieldsAndAssociations() {
        Category category = category();
        long definition = definition();
        long product = products.create(new CreateProductRequest("Inicial", category.getId(), null,
                new BigDecimal("10"), true, List.of(item(definition, "Original", "kg")))).id();
        for (List<ProductCharacteristicRequest> invalid : List.of(
                List.of(item(definition, "Uno", null), item(definition, "Dos", null)),
                List.of(item(definition, " ", null)),
                List.of(item(definition, "x".repeat(501), null)))) {
            assertThatThrownBy(() -> products.update(product, update(category, "Cambiado", invalid)))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> products.update(product,
                update(category, "Cambiado", List.of(item(Long.MAX_VALUE, "Nuevo", null)))))
                .isInstanceOf(ResourceNotFoundException.class);
        UpdateProductRequest explicitNull = update(category, "Cambiado", null);
        assertThatThrownBy(() -> products.update(product, explicitNull)).isInstanceOf(BusinessException.class);
        assertThat(products.getById(product).nombre()).isEqualTo("Inicial");
        assertThat(products.getById(product).caracteristicas().getFirst().valor()).isEqualTo("Original");
        assertThat(values.findByProductIdWithCharacteristic(product)).hasSize(1);
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
    }

    private long definition() {
        String suffix = UUID.randomUUID().toString();
        return characteristics.saveAndFlush(Characteristic.builder().nombre("Marca " + suffix)
                .nombreNormalizado(suffix).build()).getId();
    }

    private ProductCharacteristicRequest item(long id, String value, String unit) {
        return new ProductCharacteristicRequest(id, value, unit);
    }

    private UpdateProductRequest update(Category category, String name) {
        return new UpdateProductRequest(name, category.getId(), null, new BigDecimal("20"), false);
    }

    private UpdateProductRequest update(Category category, String name, List<ProductCharacteristicRequest> items) {
        return new UpdateProductRequest(name, category.getId(), null, new BigDecimal("20"), false, items);
    }
}
