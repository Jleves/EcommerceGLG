package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductCharacteristicServiceIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductRepository products;
    @Autowired ProductCharacteristicRepository values;
    @Autowired ProductService service;

    @AfterEach
    void clearAssociations() {
        values.deleteAll();
    }

    @Test
    void reusesDefinitionForTwoProductsAndKeepsValuesThroughBasicUpdates() {
        Category category = category();
        Characteristic brand = characteristic();
        var first = service.create(request(category.getId(), "Uno",
                List.of(new ProductCharacteristicRequest(brand.getId(), "  Acme  ", "  " ))));
        var second = service.create(request(category.getId(), "Dos",
                List.of(new ProductCharacteristicRequest(brand.getId(), "Otro", null))));
        assertThat(first.caracteristicas()).hasSize(1);
        assertThat(first.caracteristicas().getFirst().nombre()).isEqualTo(brand.getNombre());
        assertThat(first.caracteristicas().getFirst().valor()).isEqualTo("Acme");
        assertThat(first.caracteristicas().getFirst().unidad()).isNull();
        assertThat(second.caracteristicas().getFirst().valor()).isEqualTo("Otro");
        assertThat(values.findByProductIdWithCharacteristic(first.id()).getFirst().getValor()).isEqualTo("Acme");
        assertThat(values.findByProductIdWithCharacteristic(second.id()).getFirst().getValor()).isEqualTo("Otro");
        assertThat(service.update(first.id(), new UpdateProductRequest("Actualizado", category.getId(), null,
                new BigDecimal("12.00"), false)).caracteristicas()).hasSize(1);
        assertThat(service.deactivate(first.id()).caracteristicas().getFirst().valor()).isEqualTo("Acme");
        assertThat(service.reactivate(first.id()).caracteristicas().getFirst().valor()).isEqualTo("Acme");
    }

    @Test
    void basicAndEmptyArrayCreateNoAssociationsAndInvalidReferencesLeaveNoProduct() {
        Category category = category();
        assertThat(service.create(request(category.getId(), "Basico", null)).caracteristicas()).isEmpty();
        assertThat(service.create(request(category.getId(), "Vacio", List.of())).caracteristicas()).isEmpty();
        long before = products.count();
        var repeated = List.of(new ProductCharacteristicRequest(1L, "A", null),
                new ProductCharacteristicRequest(1L, "B", null));
        assertThatThrownBy(() -> service.create(request(category.getId(), "Duplicado", repeated)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(request(category.getId(), "Inexistente",
                List.of(new ProductCharacteristicRequest(Long.MAX_VALUE, "A", null)))))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(products.count()).isEqualTo(before);
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
    }

    private Characteristic characteristic() {
        String suffix = UUID.randomUUID().toString();
        return characteristics.saveAndFlush(Characteristic.builder().nombre("Marca " + suffix)
                .nombreNormalizado("marca-" + suffix).build());
    }

    private CreateProductRequest request(Long categoryId, String name, List<ProductCharacteristicRequest> items) {
        return new CreateProductRequest(name, categoryId, null, new BigDecimal("10.00"), true, items);
    }
}
