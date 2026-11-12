package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.service.PublicProductService;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductListingMySqlTest {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired SubcategoryRepository subcategories;
    @Autowired ProductVariantRepository variants;
    @Autowired PublicProductService publicProducts;

    @Test
    void mysqlPaginatesAndEscapesLiteralNameSearch() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Material " + suffix)
                .nombreNormalizado("material-" + suffix).icono("brick").activo(true).build());
        Product first = product(category, "A%_! " + suffix, true);
        Product second = product(category, "A%_! " + suffix, false);
        product(category, "Arena " + suffix, true);

        for (String pattern : new String[]{"%!%%", "%!_%", "%!!%"}) {
            var page = products.findPageByName(pattern, PageRequest.of(0, 1));
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getContent().getFirst().getId()).isEqualTo(first.getId());
            assertThat(products.findPageByName(pattern, PageRequest.of(1, 1)).getContent()
                    .getFirst().getId()).isEqualTo(second.getId());
        }
    }

    @Test
    void mysqlFiltersParentAndChildWithCorrectPagesInBothListings() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Material " + suffix)
                .nombreNormalizado("material-" + suffix).icono("brick").activo(true).build());
        Subcategory parent = subcategories.saveAndFlush(Subcategory.builder().category(category)
                .nombre("Padre").nombreNormalizado("padre").build());
        Subcategory child = subcategories.saveAndFlush(Subcategory.builder().category(category)
                .parentSubcategory(parent).nombre("Hijo").nombreNormalizado("hijo").build());
        Product direct = product(category, "A " + suffix, true);
        direct.setSubcategory(parent);
        products.saveAndFlush(direct);
        Product descendant = product(category, "B " + suffix, true);
        descendant.setSubcategory(child);
        products.saveAndFlush(descendant);
        product(category, "C " + suffix, true);
        for (Product product : List.of(direct, descendant)) {
            variants.saveAndFlush(ProductVariant.builder().product(product).precioReferencia(new BigDecimal("12.50"))
                    .disponible(true).activo(true).build());
        }
        var first = products.findPageByFilters(null, category.getId(), parent.getId(), PageRequest.of(0, 1));
        var second = products.findPageByFilters(null, category.getId(), parent.getId(), PageRequest.of(1, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent().getFirst().getId()).isEqualTo(direct.getId());
        assertThat(second.getContent().getFirst().getId()).isEqualTo(descendant.getId());
        assertThat(products.findPageByFilters(null, category.getId(), child.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
        var publicFirst = publicProducts.list(0, 1, null, category.getId(), parent.getId(), null, List.of());
        var publicSecond = publicProducts.list(1, 1, null, category.getId(), parent.getId(), null, List.of());
        assertThat(publicFirst.totalElements()).isEqualTo(2);
        assertThat(publicFirst.content().getFirst().id()).isEqualTo(direct.getId());
        assertThat(publicSecond.content().getFirst().id()).isEqualTo(descendant.getId());
        assertThat(publicProducts.list(0, 10, null, category.getId(), child.getId(), null, List.of())
                .totalElements()).isEqualTo(1);
    }

    private Product product(Category category, String name, boolean active) {
        return products.saveAndFlush(Product.builder().category(category).nombre(name)
                .activo(active).build());
    }
}
