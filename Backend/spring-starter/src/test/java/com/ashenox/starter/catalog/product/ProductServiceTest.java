package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.ProductCategoryInactiveException;
import com.ashenox.starter.catalog.product.service.ProductServiceImpl;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(categories, products);

    @Test
    void trimsNamePreservesDataAndSetsServerOwnedStates() {
        Category category = Category.builder().id(7L).activo(true).build();
        when(categories.lockById(7L)).thenReturn(Optional.of(category));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(11L);
            return product;
        });

        var response = service.create(request("  Arena fina  ", 7L));

        verify(products).saveAndFlush(argThat(product -> product.getCategory() == category
                && product.getNombre().equals("Arena fina")
                && product.getDescripcion().equals("Material de obra")
                && product.getPrecioReferencia().compareTo(new BigDecimal("125.50")) == 0
                && !product.isDisponible() && product.isActivo() && !product.isDestacado()));
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.nombre()).isEqualTo("Arena fina");
        assertThat(response.categoriaId()).isEqualTo(7L);
        assertThat(response.descripcion()).isEqualTo("Material de obra");
        assertThat(response.precioReferencia()).isEqualByComparingTo("125.50");
        assertThat(response.disponible()).isFalse();
        assertThat(response.activo()).isTrue();
        assertThat(response.destacado()).isFalse();
    }

    @Test
    void allowsDuplicateNamesInSameCategory() {
        when(categories.lockById(7L)).thenReturn(Optional.of(Category.builder().id(7L).activo(true).build()));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request("Arena", 7L));
        service.create(request("Arena", 7L));

        verify(products, times(2)).saveAndFlush(argThat(product -> product.getNombre().equals("Arena")));
    }

    @Test
    void missingCategoryNeverSavesProduct() {
        assertThatThrownBy(() -> service.create(request("Arena", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categories).lockById(99L);
        verifyNoInteractions(products);
    }

    @Test
    void inactiveCategoryNeverSavesProduct() {
        when(categories.lockById(7L)).thenReturn(Optional.of(Category.builder().id(7L).activo(false).build()));

        assertThatThrownBy(() -> service.create(request("Arena", 7L)))
                .isInstanceOf(ProductCategoryInactiveException.class);
        verifyNoInteractions(products);
    }

    private CreateProductRequest request(String name, Long categoryId) {
        return new CreateProductRequest(name, categoryId, "Material de obra", new BigDecimal("125.50"), false);
    }
}
