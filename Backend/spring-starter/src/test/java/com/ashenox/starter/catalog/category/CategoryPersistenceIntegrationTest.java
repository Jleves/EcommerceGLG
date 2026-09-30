package com.ashenox.starter.catalog.category;

import com.ashenox.starter.catalog.category.dto.UpdateCategoryRequest;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryPersistenceIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsCategoryAndQueriesNormalizedNameInStableOrder() {
        Category cemento = categoryRepository.saveAndFlush(category("Cemento", "cemento", true));
        Category arena = categoryRepository.saveAndFlush(category("Arena", "arena", false));
        categoryRepository.saveAndFlush(category("Ladrillos", "ladrillos", true));

        assertThat(categoryRepository.existsByNombreNormalizado("cemento")).isTrue();
        assertThat(categoryRepository.existsByNombreNormalizado("pinturas")).isFalse();
        assertThat(categoryRepository.existsByNombreNormalizadoAndIdNot("cemento", arena.getId())).isTrue();
        assertThat(categoryRepository.existsByNombreNormalizadoAndIdNot("arena", cemento.getId())).isTrue();
        assertThat(categoryRepository.findAllByOrderByNombreAscIdAsc())
                .extracting(Category::getNombre)
                .containsExactly("Arena", "Cemento", "Ladrillos");
        assertThat(categoryRepository.findAllByOrderByNombreAscIdAsc().getFirst().isActivo()).isFalse();
    }

    @Test
    void updatePersistsVisibleAndNormalizedNamesWithoutChangingActiveState() {
        Category category = categoryRepository.saveAndFlush(category("Arena", "arena", false));
        categoryRepository.saveAndFlush(category("Cemento", "cemento", true));

        var response = categoryService.update(category.getId(),
                new UpdateCategoryRequest("  LADRILLOS  ", "Descripción nueva", "brick"));
        entityManager.flush();
        entityManager.clear();

        Category persisted = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(response.nombre()).isEqualTo("LADRILLOS");
        assertThat(persisted.getNombre()).isEqualTo("LADRILLOS");
        assertThat(persisted.getNombreNormalizado()).isEqualTo("ladrillos");
        assertThat(persisted.getDescripcion()).isEqualTo("Descripción nueva");
        assertThat(persisted.getIcono()).isEqualTo("brick");
        assertThat(persisted.isActivo()).isFalse();
        assertThat(categoryRepository.existsByNombreNormalizadoAndIdNot("ladrillos", category.getId())).isFalse();
        assertThat(categoryRepository.existsByNombreNormalizadoAndIdNot("cemento", category.getId())).isTrue();
    }

    @Test
    void stateTransitionsPersistRemainListedAndPreserveDataAndRowCount() {
        Category category = category("Cemento", "cemento", true);
        category.setDescripcion("Material de obra");
        category.setIcono("cement-icon");
        category = categoryRepository.saveAndFlush(category);
        Long id = category.getId();
        long rowCount = categoryRepository.count();

        assertThat(categoryService.deactivate(id).activo()).isFalse();
        assertThat(categoryService.deactivate(id).activo()).isFalse();
        entityManager.flush();
        entityManager.clear();
        assertThat(categoryRepository.findById(id).orElseThrow().isActivo()).isFalse();
        assertThat(categoryService.list()).extracting(response -> response.id()).contains(id);
        assertThat(categoryRepository.count()).isEqualTo(rowCount);

        assertThat(categoryService.reactivate(id).activo()).isTrue();
        assertThat(categoryService.reactivate(id).activo()).isTrue();
        entityManager.flush();
        entityManager.clear();
        Category persisted = categoryRepository.findById(id).orElseThrow();
        assertThat(persisted.isActivo()).isTrue();
        assertThat(persisted.getNombre()).isEqualTo("Cemento");
        assertThat(persisted.getNombreNormalizado()).isEqualTo("cemento");
        assertThat(persisted.getDescripcion()).isEqualTo("Material de obra");
        assertThat(persisted.getIcono()).isEqualTo("cement-icon");
        assertThat(categoryRepository.count()).isEqualTo(rowCount);
    }

    private Category category(String nombre, String nombreNormalizado, boolean activo) {
        return Category.builder()
                .nombre(nombre)
                .nombreNormalizado(nombreNormalizado)
                .icono("category-icon")
                .activo(activo)
                .build();
    }
}
