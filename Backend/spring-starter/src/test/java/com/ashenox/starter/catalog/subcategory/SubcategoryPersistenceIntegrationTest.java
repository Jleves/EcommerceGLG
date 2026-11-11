package com.ashenox.starter.catalog.subcategory;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubcategoryPersistenceIntegrationTest {

    @Autowired private CategoryRepository categories;
    @Autowired private SubcategoryRepository subcategories;
    @Autowired private ProductRepository products;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void flywaySchemaAndJpaMappingPersistSubcategoryAndOptionalProductLink() {
        Category parent = category("Principal");
        Subcategory subcategory = subcategories.saveAndFlush(subcategory(parent, "Chapas", "chapas"));
        Product unclassified = products.saveAndFlush(product(parent, "Sin clasificar"));
        Product classified = product(parent, "Clasificado");
        classified.setSubcategory(subcategory);
        classified = products.saveAndFlush(classified);
        entityManager.clear();

        assertThat(subcategories.findById(subcategory.getId()).orElseThrow().getCategory().getId())
                .isEqualTo(parent.getId());
        assertThat(products.findById(unclassified.getId()).orElseThrow().getCategory().getId())
                .isEqualTo(parent.getId());
        assertThat(products.findById(unclassified.getId()).orElseThrow().getSubcategory()).isNull();
        assertThat(products.findById(classified.getId()).orElseThrow().getSubcategory().getId())
                .isEqualTo(subcategory.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ? AND category_id = ? AND subcategory_id IS NULL",
                Integer.class, unclassified.getId(), parent.getId())).isEqualTo(1);
    }

    @Test
    void repositoryQueriesKeepNamesUniqueOnlyWithinParentAndReturnStableOrder() {
        Category first = category("A");
        Category second = category("B");
        Subcategory firstZ = subcategories.saveAndFlush(subcategory(first, "Zinc", "zinc"));
        Subcategory firstA = subcategories.saveAndFlush(subcategory(first, "Acero", "acero"));
        Subcategory secondA = subcategories.saveAndFlush(subcategory(second, "Acero", "acero"));

        assertThat(subcategories.existsByCategoryIdAndNombreNormalizado(first.getId(), "acero")).isTrue();
        assertThat(subcategories.existsByCategoryIdAndNombreNormalizado(second.getId(), "zinc")).isFalse();
        assertThat(subcategories.existsByCategoryIdAndNombreNormalizadoAndIdNot(first.getId(), "acero", firstA.getId()))
                .isFalse();
        assertThat(subcategories.findByCategoryIdOrderByNombreAscIdAsc(first.getId()))
                .extracting(Subcategory::getId).containsExactly(firstA.getId(), firstZ.getId());
        Set<Long> createdIds = Set.of(firstA.getId(), firstZ.getId(), secondA.getId());
        assertThat(subcategories.findAllByOrderByCategoryIdAscNombreAscIdAsc())
                .extracting(Subcategory::getId)
                .filteredOn(createdIds::contains)
                .containsExactly(firstA.getId(), firstZ.getId(), secondA.getId());
        assertThat(subcategories.lockById(firstA.getId())).isPresent();
    }

    @Test
    void mapsParentAndFindsNamesWithinEachSiblingScope() {
        Category category = category("Jerarquía");
        Subcategory root = subcategories.saveAndFlush(subcategory(category, "Raíz", "raiz"));
        Subcategory child = subcategory(category, "Hijo", "hijo");
        child.setParentSubcategory(root);
        child = subcategories.saveAndFlush(child);
        Long childId = child.getId();
        entityManager.clear();

        assertThat(subcategories.findById(childId).orElseThrow().getParentSubcategory().getId()).isEqualTo(root.getId());
        assertThat(subcategories.findByParentSubcategoryIdOrderByNombreAscIdAsc(root.getId()))
                .extracting(Subcategory::getId).containsExactly(childId);
        assertThat(subcategories.existsByCategoryIdAndParentSubcategoryIdAndNombreNormalizado(category.getId(), root.getId(), "hijo")).isTrue();
        assertThat(subcategories.existsByCategoryIdAndParentSubcategoryIdAndNombreNormalizado(category.getId(), null, "hijo")).isFalse();
        assertThat(subcategories.existsByCategoryIdAndParentSubcategoryIdAndNombreNormalizadoAndIdNot(category.getId(), root.getId(), "hijo", childId)).isFalse();
    }

    private Category category(String name) {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder()
                .nombre(name + suffix).nombreNormalizado((name + suffix).toLowerCase())
                .icono("icon").build());
    }

    private Subcategory subcategory(Category parent, String name, String normalized) {
        return Subcategory.builder().category(parent).nombre(name)
                .nombreNormalizado(normalized).descripcion("Material").build();
    }

    private Product product(Category parent, String name) {
        return Product.builder().category(parent).nombre(name)
                .build();
    }
}
