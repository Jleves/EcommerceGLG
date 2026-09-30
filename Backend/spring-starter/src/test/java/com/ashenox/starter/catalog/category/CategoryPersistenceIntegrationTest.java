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

    private Category category(String nombre, String nombreNormalizado, boolean activo) {
        return Category.builder()
                .nombre(nombre)
                .nombreNormalizado(nombreNormalizado)
                .icono("category-icon")
                .activo(activo)
                .build();
    }
}
