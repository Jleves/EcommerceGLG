package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductCategoryInactiveException;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceIntegrationTest {
    @Autowired private CategoryRepository categories;
    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;

    @Test
    void productSurvivesDeactivationAndReactivationControlsNewCreates() {
        long initialProductCount = products.count();
        String unique = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder()
                .nombre("Categoría " + unique).nombreNormalizado("categoria-" + unique)
                .icono("icon").build());
        var request = new CreateProductRequest("  Arena  ", category.getId(), "Para obra",
                new BigDecimal("15.25"), true);

        var created = productService.create(request);
        categoryService.deactivate(category.getId());

        var existing = products.findById(created.id()).orElseThrow();
        assertThat(existing.getCategory().getId()).isEqualTo(category.getId());
        assertThat(existing.getNombre()).isEqualTo("Arena");
        assertThat(existing.getDescripcion()).isEqualTo("Para obra");
        assertThat(existing.getPrecioReferencia()).isEqualByComparingTo("15.25");
        assertThat(existing.isDisponible()).isTrue();
        assertThat(existing.isActivo()).isTrue();
        assertThat(existing.isDestacado()).isFalse();
        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ProductCategoryInactiveException.class);
        assertThat(products.count()).isEqualTo(initialProductCount + 1);

        categoryService.reactivate(category.getId());
        var second = productService.create(request);
        assertThat(second.id()).isNotEqualTo(created.id());
        assertThat(products.count()).isEqualTo(initialProductCount + 2);
        assertThat(products.findById(created.id()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void updatePersistsBasicDataAndInactiveCategoryWithoutChangingStates() {
        String unique = UUID.randomUUID().toString();
        Category originalCategory = categories.saveAndFlush(Category.builder()
                .nombre("Original " + unique).nombreNormalizado("original-" + unique)
                .icono("old").activo(true).build());
        Category inactiveCategory = categories.saveAndFlush(Category.builder()
                .nombre("Inactiva " + unique).nombreNormalizado("inactiva-" + unique)
                .icono("new").activo(false).build());
        var created = productService.create(new CreateProductRequest("Original", originalCategory.getId(),
                "Antes", new BigDecimal("10.00"), false));
        productService.create(new CreateProductRequest("Arena fina", originalCategory.getId(),
                "Duplicado", new BigDecimal("11.00"), false));
        var updatedInActiveCategory = productService.update(created.id(), new UpdateProductRequest(
                "Temporal", originalCategory.getId(), "Activo", new BigDecimal("12.25"), false));
        assertThat(updatedInActiveCategory.categoriaId()).isEqualTo(originalCategory.getId());
        assertThat(products.findById(created.id()).orElseThrow().getNombre()).isEqualTo("Temporal");
        var product = products.findById(created.id()).orElseThrow();
        product.setActivo(false);
        product.setDestacado(true);
        products.saveAndFlush(product);
        var createdAt = product.getCreatedAt();

        var updated = productService.update(created.id(), new UpdateProductRequest("  Arena fina  ",
                inactiveCategory.getId(), null, new BigDecimal("1234567890123.45"), true));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.nombre()).isEqualTo("Arena fina");
        assertThat(updated.categoriaId()).isEqualTo(inactiveCategory.getId());
        assertThat(updated.descripcion()).isNull();
        assertThat(updated.precioReferencia()).isEqualByComparingTo("1234567890123.45");
        assertThat(updated.disponible()).isTrue();
        assertThat(updated.activo()).isFalse();
        assertThat(updated.destacado()).isTrue();

        var reloaded = products.findById(created.id()).orElseThrow();
        assertThat(reloaded.getCategory().getId()).isEqualTo(inactiveCategory.getId());
        assertThat(reloaded.getNombre()).isEqualTo("Arena fina");
        assertThat(reloaded.getDescripcion()).isNull();
        assertThat(reloaded.getPrecioReferencia()).isEqualByComparingTo("1234567890123.45");
        assertThat(reloaded.isDisponible()).isTrue();
        assertThat(reloaded.isActivo()).isFalse();
        assertThat(reloaded.isDestacado()).isTrue();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void missingCategoryLeavesPersistedProductUnchanged() {
        String unique = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder()
                .nombre("Materiales " + unique).nombreNormalizado("materiales-" + unique)
                .icono("icon").activo(true).build());
        var created = productService.create(new CreateProductRequest("Original", category.getId(),
                "Antes", new BigDecimal("10.00"), false));

        assertThatThrownBy(() -> productService.update(created.id(), new UpdateProductRequest(
                "Nuevo", Long.MAX_VALUE, null, new BigDecimal("12.00"), true)))
                .isInstanceOf(com.ashenox.starter.shared.error.ResourceNotFoundException.class);

        var unchanged = products.findById(created.id()).orElseThrow();
        assertThat(unchanged.getCategory().getId()).isEqualTo(category.getId());
        assertThat(unchanged.getNombre()).isEqualTo("Original");
        assertThat(unchanged.getDescripcion()).isEqualTo("Antes");
        assertThat(unchanged.getPrecioReferencia()).isEqualByComparingTo("10.00");
        assertThat(unchanged.isDisponible()).isFalse();
    }
}
