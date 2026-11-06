package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:publicofferhttptest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class PublicOfferHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired OfferRepository offers;

    @BeforeEach
    void clean() {
        offers.deleteAll();
        variants.deleteAll();
        products.deleteAll();
        categories.deleteAll();
    }

    @Test
    void anonymousListingValidatesPageAndHidesInactiveRowsUntilReactivation() throws Exception {
        Product active = product("Activo", true);
        ProductVariant unavailable = variant(active, "100.00", false, true);
        offer(unavailable, "80.00");
        Product hidden = product("Oculto", false);
        ProductVariant hiddenVariant = variant(hidden, "100.00", true, true);
        offer(hiddenVariant, "10.00");
        ProductVariant inactive = variant(active, "100.00", true, false);
        offer(inactive, "5.00");

        mvc.perform(get("/api/public/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].selectedVariantId").value(unavailable.getId()))
                .andExpect(jsonPath("$.content[0].disponible").value(false))
                .andExpect(jsonPath("$.content[0].precioReferencia").value(100.00))
                .andExpect(jsonPath("$.content[0].precioOferta").value(80.00))
                .andExpect(jsonPath("$.content[0].porcentajeDescuento").value(20));
        mvc.perform(get("/api/public/offers").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/offers").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/offers").param("size", "101")).andExpect(status().isBadRequest());

        hidden.setActivo(true);
        products.saveAndFlush(hidden);
        mvc.perform(get("/api/public/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void catalogRelatedAndDetailChooseBestOfferWithoutChangingBasePrice() throws Exception {
        Product main = product("Arena", true);
        ProductVariant normal = variant(main, "10.00", true, true);
        ProductVariant lowerDiscount = variant(main, "100.00", true, true);
        offer(lowerDiscount, "80.00");
        ProductVariant best = variant(main, "200.00", false, true);
        offer(best, "100.00");
        ProductVariant inactive = variant(main, "100.00", true, false);
        offer(inactive, "1.00");
        Product related = product("Piedra", true);
        ProductVariant relatedVariant = variant(related, "20.00", true, true);
        offer(relatedVariant, "19.99");
        Product plain = product("Zinc", true);
        variant(plain, "15.00", true, true);

        mvc.perform(get("/api/public/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].selectedVariantId").value(best.getId()))
                .andExpect(jsonPath("$.content[0].precioReferencia").value(200.00))
                .andExpect(jsonPath("$.content[0].precioOferta").value(100.00))
                .andExpect(jsonPath("$.content[2].precioOferta").value((Object) null));
        mvc.perform(get("/api/public/products/" + main.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedVariantId").value(best.getId()))
                .andExpect(jsonPath("$.precioReferencia").value(200.00))
                .andExpect(jsonPath("$.precioOferta").value(100.00))
                .andExpect(jsonPath("$.porcentajeDescuento").value(50))
                .andExpect(jsonPath("$.variantes.length()").value(3))
                .andExpect(jsonPath("$.variantes[0].id").value(normal.getId()))
                .andExpect(jsonPath("$.variantes[0].precioOferta").value((Object) null))
                .andExpect(jsonPath("$.variantes[1].precioOferta").value(80.00))
                .andExpect(jsonPath("$.variantes[2].precioOferta").value(100.00));
        mvc.perform(get("/api/public/products/" + main.getId() + "/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(related.getId()))
                .andExpect(jsonPath("$.content[0].precioOferta").value(19.99));
        mvc.perform(get("/api/public/products/" + plain.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioOferta").value((Object) null));
    }

    @Test
    void tiedOffersPreferAvailableThenLowestVariantIdAndSmallDiscountFloorsToZero() throws Exception {
        Product product = product("Pintura", true);
        ProductVariant unavailable = variant(product, "100.00", false, true);
        offer(unavailable, "75.00");
        ProductVariant available = variant(product, "200.00", true, true);
        offer(available, "150.00");
        ProductVariant sameDiscount = variant(product, "400.00", true, true);
        offer(sameDiscount, "300.00");
        Product small = product("Cal", true);
        offer(variant(small, "100.00", true, true), "99.99");

        mvc.perform(get("/api/public/products/" + product.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedVariantId").value(available.getId()));
        mvc.perform(get("/api/public/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].selectedVariantId").value(available.getId()))
                .andExpect(jsonPath("$.content[1].porcentajeDescuento").value(0));
        mvc.perform(get("/api/public/offers").param("page", "3").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private Product product(String name, boolean active) {
        Category category = categories.findAll().stream().findFirst().orElseGet(() -> categories.saveAndFlush(
                Category.builder().nombre("Materiales").nombreNormalizado("materiales-" + UUID.randomUUID())
                        .icono("brick").activo(true).build()));
        return products.saveAndFlush(Product.builder().category(category).nombre(name).activo(active).build());
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
