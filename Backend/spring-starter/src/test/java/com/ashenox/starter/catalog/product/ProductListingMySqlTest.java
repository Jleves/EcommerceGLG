package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
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

    private Product product(Category category, String name, boolean active) {
        return products.saveAndFlush(Product.builder().category(category).nombre(name)
                .activo(active).build());
    }
}
