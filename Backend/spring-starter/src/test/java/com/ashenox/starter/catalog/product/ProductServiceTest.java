package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
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

    @Test
    void updateChangesBasicDataAndKeepsServerOwnedStates() {
        Category previous = Category.builder().id(7L).activo(true).build();
        Category destination = Category.builder().id(8L).activo(false).build();
        Product product = Product.builder().id(11L).category(previous).nombre("Original")
                .descripcion("Anterior").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).activo(false).destacado(true).build();
        when(products.lockById(11L)).thenReturn(Optional.of(product));
        when(categories.findById(8L)).thenReturn(Optional.of(destination));
        when(products.saveAndFlush(product)).thenReturn(product);

        var response = service.update(11L, new UpdateProductRequest(
                "  Arena fina  ", 8L, null, new BigDecimal("125.50"), true));

        verify(products).saveAndFlush(argThat(saved -> saved.getCategory() == destination
                && saved.getNombre().equals("Arena fina") && saved.getDescripcion() == null
                && saved.getPrecioReferencia().compareTo(new BigDecimal("125.50")) == 0
                && saved.isDisponible() && !saved.isActivo() && saved.isDestacado()));
        verify(categories).findById(8L);
        verify(categories, never()).lockById(8L);
        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.categoriaId()).isEqualTo(8L);
        assertThat(response.nombre()).isEqualTo("Arena fina");
        assertThat(response.descripcion()).isNull();
        assertThat(response.precioReferencia()).isEqualByComparingTo("125.50");
        assertThat(response.disponible()).isTrue();
        assertThat(response.activo()).isFalse();
        assertThat(response.destacado()).isTrue();
    }

    @Test
    void missingProductDoesNotLookUpCategoryOrSave() {
        assertThatThrownBy(() -> service.update(99L, updateRequest("Arena", 7L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(products).lockById(99L);
        verifyNoInteractions(categories);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void missingCategoryDoesNotSaveOrChangeProduct() {
        Category previous = Category.builder().id(7L).activo(true).build();
        Product product = Product.builder().id(11L).category(previous).nombre("Original")
                .descripcion("Anterior").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).activo(true).destacado(false).build();
        when(products.lockById(11L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.update(11L, updateRequest("Nuevo", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categories).findById(99L);
        verify(products, never()).saveAndFlush(any());
        assertThat(product.getCategory()).isSameAs(previous);
        assertThat(product.getNombre()).isEqualTo("Original");
        assertThat(product.getDescripcion()).isEqualTo("Anterior");
    }

    @Test
    void updateAllowsNameAlreadyUsedByAnotherProduct() {
        Product product = Product.builder().id(11L).category(Category.builder().id(7L).build())
                .nombre("Original").descripcion("Antes").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).build();
        when(products.lockById(11L)).thenReturn(Optional.of(product));
        when(categories.findById(7L)).thenReturn(Optional.of(product.getCategory()));
        when(products.saveAndFlush(product)).thenReturn(product);

        service.update(11L, updateRequest("Arena", 7L));

        verify(products).lockById(11L);
        verify(products).saveAndFlush(argThat(saved -> saved.getNombre().equals("Arena")));
        verifyNoMoreInteractions(products);
    }

    @Test
    void statusTransitionsAreIdempotentAndKeepAllOtherData() {
        Category inactiveCategory = Category.builder().id(7L).activo(false).build();
        Product product = Product.builder().id(11L).category(inactiveCategory).nombre("Arena")
                .descripcion("Para obra").precioReferencia(new BigDecimal("125.50"))
                .disponible(false).activo(true).destacado(true).build();
        when(products.lockById(11L)).thenReturn(Optional.of(product));
        when(products.saveAndFlush(product)).thenReturn(product);

        assertThat(service.deactivate(11L).activo()).isFalse();
        assertThat(service.deactivate(11L).activo()).isFalse();
        assertThat(service.reactivate(11L).activo()).isTrue();
        var repeated = service.reactivate(11L);

        assertThat(repeated.activo()).isTrue();
        assertThat(repeated.id()).isEqualTo(11L);
        assertThat(repeated.categoriaId()).isEqualTo(7L);
        assertThat(repeated.nombre()).isEqualTo("Arena");
        assertThat(repeated.descripcion()).isEqualTo("Para obra");
        assertThat(repeated.precioReferencia()).isEqualByComparingTo("125.50");
        assertThat(repeated.disponible()).isFalse();
        assertThat(repeated.destacado()).isTrue();
        verify(products, times(4)).lockById(11L);
        verify(products, times(4)).saveAndFlush(product);
        verifyNoInteractions(categories);
    }

    @Test
    void missingProductCannotChangeStatusOrCreateRows() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(products, times(2)).lockById(99L);
        verify(products, never()).saveAndFlush(any());
        verifyNoInteractions(categories);
    }

    private CreateProductRequest request(String name, Long categoryId) {
        return new CreateProductRequest(name, categoryId, "Material de obra", new BigDecimal("125.50"), false);
    }

    private UpdateProductRequest updateRequest(String name, Long categoryId) {
        return new UpdateProductRequest(name, categoryId, "Material de obra", new BigDecimal("125.50"), false);
    }
}
