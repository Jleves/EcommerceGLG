package com.ashenox.starter.catalog.category;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
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

    @Test
    void persistsCategoryAndQueriesNormalizedNameInStableOrder() {
        categoryRepository.saveAndFlush(category("Cemento", "cemento", true));
        categoryRepository.saveAndFlush(category("Arena", "arena", false));
        categoryRepository.saveAndFlush(category("Ladrillos", "ladrillos", true));

        assertThat(categoryRepository.existsByNombreNormalizado("cemento")).isTrue();
        assertThat(categoryRepository.existsByNombreNormalizado("pinturas")).isFalse();
        assertThat(categoryRepository.findAllByOrderByNombreAscIdAsc())
                .extracting(Category::getNombre)
                .containsExactly("Arena", "Cemento", "Ladrillos");
        assertThat(categoryRepository.findAllByOrderByNombreAscIdAsc().getFirst().isActivo()).isFalse();
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
