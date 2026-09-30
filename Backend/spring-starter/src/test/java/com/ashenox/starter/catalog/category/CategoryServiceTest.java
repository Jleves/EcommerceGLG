package com.ashenox.starter.catalog.category;

import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.dto.UpdateCategoryRequest;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryConflictException;
import com.ashenox.starter.catalog.category.service.CategoryServiceImpl;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(repository);

    @Test
    void createsActiveCategoryWithTrimmedNameAndNormalizedKey() {
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(17L);
            return category;
        });

        var response = service.create(new CreateCategoryRequest("  CEMENTO  ", null, "cement-icon"));

        verify(repository).existsByNombreNormalizado("cemento");
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(category ->
                category.getNombre().equals("CEMENTO")
                        && category.getNombreNormalizado().equals("cemento")
                        && category.getDescripcion() == null
                        && category.getIcono().equals("cement-icon")
                        && category.isActivo()));
        assertThat(response.id()).isEqualTo(17L);
        assertThat(response.nombre()).isEqualTo("CEMENTO");
        assertThat(response.descripcion()).isNull();
        assertThat(response.icono()).isEqualTo("cement-icon");
        assertThat(response.activo()).isTrue();
    }

    @Test
    void rejectsExistingNameBeforeSavingRegardlessOfActiveState() {
        for (boolean existingActive : new boolean[]{true, false}) {
            Category existing = category(1L, "Cemento", existingActive);
            when(repository.existsByNombreNormalizado(existing.getNombreNormalizado())).thenReturn(true);

            assertThatThrownBy(() -> service.create(new CreateCategoryRequest(" cemento ", null, "icon")))
                    .isInstanceOf(CategoryConflictException.class);
        }
        verify(repository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void updatesOwnNameWithoutConflictAndPreservesInactiveState() {
        Category existing = category(7L, "Cemento", false);
        existing.setDescripcion("Anterior");
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        var response = service.update(7L, new UpdateCategoryRequest("  CEMENTO  ", null, "new-icon"));

        verify(repository).existsByNombreNormalizadoAndIdNot("cemento", 7L);
        assertThat(response.nombre()).isEqualTo("CEMENTO");
        assertThat(response.descripcion()).isNull();
        assertThat(response.icono()).isEqualTo("new-icon");
        assertThat(response.activo()).isFalse();
        assertThat(existing.getNombreNormalizado()).isEqualTo("cemento");
    }

    @Test
    void rejectsAnotherCategoryNameWithoutChangingTarget() {
        Category target = category(7L, "Arena", true);
        when(repository.findById(7L)).thenReturn(Optional.of(target));
        when(repository.existsByNombreNormalizadoAndIdNot("cemento", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, new UpdateCategoryRequest("  CEMENTO ", "Nueva", "icon")))
                .isInstanceOf(CategoryConflictException.class);
        assertThat(target.getNombre()).isEqualTo("Arena");
        assertThat(target.isActivo()).isTrue();
        verify(repository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void rejectsMissingCategoryBeforeCheckingNameOrSaving() {
        assertThatThrownBy(() -> service.update(99L, new UpdateCategoryRequest("Cemento", null, "icon")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).existsByNombreNormalizadoAndIdNot(any(), any());
        verify(repository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void deactivatesAndReactivatesWithoutChangingOtherFields() {
        Category category = category(7L, "Cemento", true);
        category.setDescripcion("Material de obra");
        when(repository.lockById(7L)).thenReturn(Optional.of(category));
        when(repository.saveAndFlush(category)).thenReturn(category);

        var deactivated = service.deactivate(7L);
        assertThat(deactivated.activo()).isFalse();
        var repeatedDeactivation = service.deactivate(7L);
        assertThat(repeatedDeactivation).isEqualTo(deactivated);

        var reactivated = service.reactivate(7L);
        assertThat(reactivated.activo()).isTrue();
        var repeatedReactivation = service.reactivate(7L);
        assertThat(repeatedReactivation).isEqualTo(reactivated);

        assertThat(category.getNombre()).isEqualTo("Cemento");
        assertThat(category.getNombreNormalizado()).isEqualTo("cemento");
        assertThat(category.getDescripcion()).isEqualTo("Material de obra");
        assertThat(category.getIcono()).isEqualTo("icon");
        verify(repository, org.mockito.Mockito.times(4)).saveAndFlush(category);
        verify(repository, org.mockito.Mockito.times(4)).lockById(7L);
        verify(repository, never()).delete(any(Category.class));
    }

    @Test
    void rejectsMissingCategoryForBothStateTransitions() {
        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, org.mockito.Mockito.times(2)).lockById(99L);
        verify(repository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void listsEmptyResultAndMapsRepositoryOrderIncludingInactiveCategories() {
        when(repository.findAllByOrderByNombreAscIdAsc()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();

        when(repository.findAllByOrderByNombreAscIdAsc()).thenReturn(List.of(
                category(2L, "Arena", false), category(1L, "Cemento", true)));
        var result = service.list();
        assertThat(result).extracting(response -> response.nombre()).containsExactly("Arena", "Cemento");
        assertThat(result).extracting(response -> response.activo()).containsExactly(false, true);
        assertThat(result).extracting(response -> response.id()).containsExactly(2L, 1L);
    }

    private Category category(Long id, String nombre, boolean activo) {
        return Category.builder().id(id).nombre(nombre).nombreNormalizado(nombre.toLowerCase())
                .icono("icon").activo(activo).build();
    }
}
