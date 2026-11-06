package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
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
class PublicOfferPageMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired OfferRepository offers;

    @Test
    void ordersFullPrecisionBeforePagingAndCountsDistinctProducts() {
        Category category = categories.saveAndFlush(Category.builder().nombre("Materiales")
                .nombreNormalizado("materiales-" + UUID.randomUUID()).icono("brick").activo(true).build());
        Product first = product(category, "Zinc");
        Product second = product(category, "Arena");
        Product tied = product(category, "Cemento");
        offer(variant(first, "10000.00", true, true), "8999.00"); // 10.01%
        offer(variant(first, "100.00", true, true), "80.00"); // 20%
        offer(variant(second, "10000.00", true, true), "7999.00"); // 20.01%
        offer(variant(tied, "100.00", true, true), "80.00"); // 20%
        Product inactive = product(category, "Oculto");
        inactive.setActivo(false);
        products.saveAndFlush(inactive);
        offer(variant(inactive, "100.00", true, true), "1.00");
        offer(variant(second, "100.00", true, false), "1.00");

        var page0 = offers.findPublicProductPage(PageRequest.of(0, 1));
        var page1 = offers.findPublicProductPage(PageRequest.of(1, 1));
        var page2 = offers.findPublicProductPage(PageRequest.of(2, 1));
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getContent()).containsExactly(second.getId());
        assertThat(page1.getContent()).containsExactly(first.getId());
        assertThat(page2.getContent()).containsExactly(tied.getId());
    }

    private Product product(Category category, String name) {
        return products.saveAndFlush(Product.builder().category(category).nombre(name).activo(true).build());
    }

    private ProductVariant variant(Product product, String base, boolean available, boolean active) {
        return variants.saveAndFlush(ProductVariant.builder().product(product)
                .precioReferencia(new BigDecimal(base)).disponible(available).activo(active).build());
    }

    private void offer(ProductVariant variant, String price) {
        VariantOffer offer = new VariantOffer();
        offer.setVariant(variant);
        offer.setPrecioOferta(new BigDecimal(price));
        offers.saveAndFlush(offer);
    }
}
