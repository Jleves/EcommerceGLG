package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
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
        assertThat(products.count()).isEqualTo(1);

        categoryService.reactivate(category.getId());
        var second = productService.create(request);
        assertThat(second.id()).isNotEqualTo(created.id());
        assertThat(products.count()).isEqualTo(2);
        assertThat(products.findById(created.id()).orElseThrow().isActivo()).isTrue();
    }
}
