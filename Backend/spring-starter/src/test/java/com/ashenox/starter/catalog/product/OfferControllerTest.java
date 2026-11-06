package com.ashenox.starter.catalog.product;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.offer.service.OfferConflictException;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.variant.dto.UpdateVariantRequest;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfferControllerTest {
    private static final String PATH = "/api/catalog/offers";

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired OfferRepository offers;
    @Autowired ProductService productService;
    @Autowired ProductVariantService variantService;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;

    @BeforeEach
    void clearOffers() {
        offers.deleteAll();
    }

    @Test
    void allowedRolesCreateEditListAndPhysicallyDeleteOffers() throws Exception {
        ProductVariant variant = variant(true, true, false, "100.00");
        Cookie admin = access(Role.ADMIN);
        Cookie regular = access(Role.USER);
        Cookie superAdmin = access(Role.SUPER_ADMIN);

        String response = mvc.perform(post(PATH).cookie(regular).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(variant.getId(), "75.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productoId").value(variant.getProduct().getId()))
                .andExpect(jsonPath("$.varianteId").value(variant.getId()))
                .andExpect(jsonPath("$.precioReferencia").value(100.00))
                .andExpect(jsonPath("$.precioOferta").value(75.00))
                .andExpect(jsonPath("$.porcentajeDescuento").value(25))
                .andExpect(jsonPath("$.disponible").value(false))
                .andReturn().getResponse().getContentAsString();
        Long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").longValue();

        mvc.perform(get(PATH + "?page=0&size=20").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(put(PATH + "/" + id).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":60.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.porcentajeDescuento").value(40));
        assertThat(variants.findById(variant.getId()).orElseThrow().getPrecioReferencia()).isEqualByComparingTo("100.00");
        mvc.perform(delete(PATH + "/" + id).cookie(superAdmin).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(offers.findById(id)).isEmpty();
        mvc.perform(get(PATH).cookie(admin)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidInputConflictAndMissingIdsUseExistingApiErrors() throws Exception {
        ProductVariant variant = variant(true, true, true, "100.00");
        Cookie access = access(Role.ADMIN);
        for (String price : new String[] {"null", "0", "-1", "1.001", "12345678901234.00"}) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(variant.getId(), price)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String price : new String[] {"100.00", "110.00"}) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(variant.getId(), price)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        }
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(999999L, "1.00")))
                .andExpect(status().isNotFound());
        mvc.perform(get(PATH + "?page=-1").cookie(access)).andExpect(status().isBadRequest());
        mvc.perform(put(PATH + "/999999").cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":1.00}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/999999").cookie(access).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(offers.count()).isZero();
    }

    @Test
    void administrativeListOrdersAndPagesOffersIncludingUnavailableVariants() throws Exception {
        ProductVariant later = variant(true, true, true, "100.00");
        ProductVariant earlier = variant(true, true, false, "100.00");
        later.getProduct().setNombre("Zinc");
        earlier.getProduct().setNombre("Arena");
        products.saveAndFlush(later.getProduct());
        products.saveAndFlush(earlier.getProduct());
        Cookie access = access(Role.ADMIN);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(later.getId(), "80.00"))).andExpect(status().isCreated());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(earlier.getId(), "70.00"))).andExpect(status().isCreated());
        mvc.perform(get(PATH + "?page=0&size=1").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].productoNombre").value("Arena"))
                .andExpect(jsonPath("$.content[0].disponible").value(false));
        mvc.perform(get(PATH + "?page=1&size=1").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].productoNombre").value("Zinc"));
    }

    @Test
    void byProductsReturnsOnlyRequestedOffersIncludingHiddenAndProtectsAccess() throws Exception {
        ProductVariant visible = variant(true, true, true, "100.00");
        ProductVariant hidden = variant(false, true, false, "200.00");
        ProductVariant other = variant(true, true, true, "300.00");
        Cookie admin = access(Role.ADMIN);
        for (ProductVariant variant : new ProductVariant[] {visible, hidden, other}) {
            var offer = new com.ashenox.starter.catalog.product.offer.model.VariantOffer();
            offer.setVariant(variant);
            offer.setPrecioOferta(new BigDecimal("50.00"));
            offers.saveAndFlush(offer);
        }
        String query = PATH + "/by-products?productIds=" + visible.getProduct().getId()
                + "&productIds=" + hidden.getProduct().getId();
        for (Role role : new Role[] {Role.SUPER_ADMIN, Role.ADMIN, Role.USER}) {
            mvc.perform(get(query).cookie(access(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].varianteId").value(visible.getId()))
                    .andExpect(jsonPath("$[1].varianteId").value(hidden.getId()))
                    .andExpect(jsonPath("$[1].productoActivo").value(false))
                    .andExpect(jsonPath("$[2]").doesNotExist());
        }
        mvc.perform(get(PATH + "/by-products?productIds=" + other.getProduct().getId()).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].varianteId").value(other.getId()));
        mvc.perform(get(PATH + "/by-products?productIds=999999").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(PATH + "/by-products?productIds=-1").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(query)).andExpect(status().isUnauthorized());
        mvc.perform(get(query).with(user("unsupported").roles("OTHER"))).andExpect(status().isForbidden());
    }

    @Test
    void duplicateAndInactiveCreationConflictButExistingOfferCanBeEditedAndDeleted() throws Exception {
        ProductVariant variant = variant(true, true, true, "100.00");
        Cookie access = access(Role.USER);
        String response = mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(variant.getId(), "75.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").longValue();
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(variant.getId(), "70.00")))
                .andExpect(status().isConflict());
        mvc.perform(put(PATH + "/" + id).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":100.00}"))
                .andExpect(status().isConflict());
        mvc.perform(put(PATH + "/" + id).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":0}"))
                .andExpect(status().isBadRequest());

        productService.deactivate(variant.getProduct().getId());
        variant.setActivo(false);
        variants.saveAndFlush(variant);
        mvc.perform(get(PATH).cookie(access)).andExpect(jsonPath("$.content[0].productoActivo").value(false));
        mvc.perform(get(PATH).cookie(access)).andExpect(jsonPath("$.content[0].varianteActiva").value(false));
        mvc.perform(put(PATH + "/" + id).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":65.00}"))
                .andExpect(status().isOk());
        mvc.perform(delete(PATH + "/" + id).cookie(access).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(variant.getId(), "60.00")))
                .andExpect(status().isConflict());

        ProductVariant another = variant(false, true, true, "100.00");
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(another.getId(), "60.00")))
                .andExpect(status().isConflict());
        ProductVariant inactiveVariant = variant(true, false, true, "100.00");
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(inactiveVariant.getId(), "60.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void basePriceEditsKeepFixedOfferAndRejectInvalidatingChanges() throws Exception {
        ProductVariant variant = variant(true, true, true, "100.00");
        Cookie access = access(Role.ADMIN);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(variant.getId(), "75.00")))
                .andExpect(status().isCreated());
        Long productId = variant.getProduct().getId();
        Long categoryId = variant.getProduct().getCategory().getId();
        productService.update(productId, new UpdateProductRequest("Producto actualizado", categoryId,
                null, new BigDecimal("150.00"), true));
        assertThat(offers.findByVariantId(variant.getId()).orElseThrow().getPrecioOferta()).isEqualByComparingTo("75.00");
        assertThatThrownBy(() -> productService.update(productId, new UpdateProductRequest("No guardar", categoryId,
                null, new BigDecimal("75.00"), true))).isInstanceOf(OfferConflictException.class);
        assertThat(products.findById(productId).orElseThrow().getNombre()).isEqualTo("Producto actualizado");
        assertThatThrownBy(() -> variantService.update(productId, variant.getId(),
                new UpdateVariantRequest(null, new BigDecimal("70.00"), true, null, null, null)))
                .isInstanceOf(OfferConflictException.class);
        variantService.update(productId, variant.getId(),
                new UpdateVariantRequest(null, new BigDecimal("120.00"), true, null, null, null));
        assertThat(variants.findById(variant.getId()).orElseThrow().getPrecioReferencia()).isEqualByComparingTo("120.00");
        mvc.perform(get(PATH).cookie(access)).andExpect(jsonPath("$.content[0].porcentajeDescuento").value(37));
    }

    @Test
    void anonymousUnsupportedRoleAndMissingCsrfCannotMutate() throws Exception {
        ProductVariant variant = variant(true, true, true, "100.00");
        String body = createBody(variant.getId(), "75.00");
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user("unsupported").roles("OTHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).cookie(access(Role.USER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(PATH + "/999999").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioOferta\":50.00}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + "/999999").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH + "/999999").with(user("unsupported").roles("OTHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioOferta\":50.00}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/999999").cookie(access(Role.USER)))
                .andExpect(status().isForbidden());
        assertThat(offers.count()).isZero();
    }

    private ProductVariant variant(boolean productActive, boolean variantActive, boolean available, String price) {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("brick").activo(true).build());
        Product product = products.saveAndFlush(Product.builder().category(category).nombre("Producto " + suffix)
                .activo(productActive).build());
        return variants.saveAndFlush(ProductVariant.builder().product(product).precioReferencia(new BigDecimal(price))
                .activo(variantActive).disponible(available).build());
    }

    private String createBody(Long variantId, String price) {
        return "{\"variantId\":" + variantId + ",\"precioOferta\":" + price + "}";
    }

    private Cookie access(Role role) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(role).enabled(true).build());
        String sessionId = sessions.createSession(user).session().getId();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), sessionId));
    }
}
