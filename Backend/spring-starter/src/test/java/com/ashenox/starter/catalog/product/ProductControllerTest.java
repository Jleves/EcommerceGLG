package com.ashenox.starter.catalog.product;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductControllerTest {
    private static final String PATH = "/api/catalog/products";
    private static final String CATEGORIES = "/api/catalog/categories";

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;

    @BeforeEach
    void clean() {
        products.deleteAll();
        categories.deleteAll();
    }

    @Test
    void everyAllowedRoleCreatesPersistedProductWithServerOwnedStates() throws Exception {
        Category category = category(true);
        for (Role role : Role.values()) {
            mvc.perform(post(PATH).cookie(access(role)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body(category.getId(), "  Arena " + role + "  ", "12.50", true)
                                    .replace("\"disponible\":true", "\"disponible\":true,\"activo\":false,\"destacado\":true")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.nombre").value("Arena " + role))
                    .andExpect(jsonPath("$.categoriaId").value(category.getId()))
                    .andExpect(jsonPath("$.descripcion").value("Material"))
                    .andExpect(jsonPath("$.precioReferencia").value(12.50))
                    .andExpect(jsonPath("$.disponible").value(true))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.destacado").value(false));
        }
        assertThat(products.count()).isEqualTo(3);
        products.findAll().forEach(product -> {
            assertThat(product.getCategory().getId()).isEqualTo(category.getId());
            assertThat(product.getPrecioReferencia()).isEqualByComparingTo(new BigDecimal("12.50"));
            assertThat(product.isDisponible()).isTrue();
            assertThat(product.isActivo()).isTrue();
            assertThat(product.isDestacado()).isFalse();
        });
    }

    @Test
    void sessionCsrfAndRoleAreRequiredBeforeWriting() throws Exception {
        Category category = category(true);
        String body = body(category.getId(), "Arena", "12.50", true);
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).cookie(access(Role.USER)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("unsupported").roles("OTHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(products.count()).isZero();
    }

    @Test
    void invalidFieldsAndMalformedJsonReturnApiErrorsWithoutWrites() throws Exception {
        Category category = category(true);
        Cookie access = access(Role.ADMIN);
        String valid = body(category.getId(), "Arena", "12.50", true);
        String[] invalid = {
                valid.replace("\"Arena\"", "\"   \""),
                valid.replace("\"Arena\"", "\"" + "x".repeat(161) + "\""),
                valid.replace("\"categoriaId\":" + category.getId(), "\"categoriaId\":null"),
                valid.replace("\"categoriaId\":" + category.getId(), "\"categoriaId\":0"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":null"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":0"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":-1"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":12.501"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":12345678901234.00"),
                valid.replace("\"disponible\":true", "\"disponible\":null"),
                valid.replace("\"Material\"", "\"" + "x".repeat(1001) + "\"")
        };
        for (String request : invalid) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(products.count()).isZero();
    }

    @Test
    void missingAndInactiveCategoryReturnApiErrorsWithoutWrites() throws Exception {
        Category category = category(false);
        Cookie access = access(Role.USER);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(99999999L, "Arena", "12.50", true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(category.getId(), "Arena", "12.50", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(products.count()).isZero();
    }

    @Test
    void categoryTransitionsKeepExistingProductAndControlFurtherCreation() throws Exception {
        Category category = category(true);
        Cookie access = access(Role.ADMIN);
        String body = body(category.getId(), "Arena", "12.50", true);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post(CATEGORIES + "/" + category.getId() + "/deactivate").cookie(access).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        assertThat(products.count()).isEqualTo(1);
        var original = products.findAll().getFirst();
        assertThat(original.getCategory().getId()).isEqualTo(category.getId());
        assertThat(original.isActivo()).isTrue();
        assertThat(original.getPrecioReferencia()).isEqualByComparingTo("12.50");
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post(CATEGORIES + "/" + category.getId() + "/reactivate").cookie(access).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(get(CATEGORIES).cookie(access)).andExpect(status().isOk());
        assertThat(products.count()).isEqualTo(2);
    }

    @Test
    void everyAllowedRoleCanEditAndChangeStatusWithoutLosingData() throws Exception {
        Category activeCategory = category(true);
        Category inactiveCategory = categories.saveAndFlush(Category.builder().nombre("Inactiva")
                .nombreNormalizado("inactiva").icono("brick").activo(false).build());
        for (Role role : Role.values()) {
            Product product = products.saveAndFlush(Product.builder().category(activeCategory)
                    .nombre("Original " + role).descripcion("Antes")
                    .precioReferencia(new BigDecimal("10.00")).disponible(false)
                    .activo(true).destacado(true).build());
            Cookie access = access(role);
            String item = PATH + "/" + product.getId();
            String update = body(inactiveCategory.getId(), "  Arena " + role + "  ", "125.50", true)
                    .replace("\"disponible\":true", "\"disponible\":true,\"activo\":false,\"destacado\":false");

            mvc.perform(put(item).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(product.getId()))
                    .andExpect(jsonPath("$.nombre").value("Arena " + role))
                    .andExpect(jsonPath("$.categoriaId").value(inactiveCategory.getId()))
                    .andExpect(jsonPath("$.descripcion").value("Material"))
                    .andExpect(jsonPath("$.precioReferencia").value(125.50))
                    .andExpect(jsonPath("$.disponible").value(true))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.destacado").value(true));
            for (int repetition = 0; repetition < 2; repetition++) {
                mvc.perform(post(item + "/deactivate").cookie(access).with(csrf()))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(product.getId()))
                        .andExpect(jsonPath("$.activo").value(false));
            }
            for (int repetition = 0; repetition < 2; repetition++) {
                mvc.perform(post(item + "/reactivate").cookie(access).with(csrf()))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(product.getId()))
                        .andExpect(jsonPath("$.activo").value(true));
            }
            Product saved = products.findById(product.getId()).orElseThrow();
            assertThat(saved.getCategory().getId()).isEqualTo(inactiveCategory.getId());
            assertThat(saved.getNombre()).isEqualTo("Arena " + role);
            assertThat(saved.getDescripcion()).isEqualTo("Material");
            assertThat(saved.getPrecioReferencia()).isEqualByComparingTo("125.50");
            assertThat(saved.isDisponible()).isTrue();
            assertThat(saved.isActivo()).isTrue();
            assertThat(saved.isDestacado()).isTrue();
        }
        assertThat(products.count()).isEqualTo(3);
    }

    @Test
    void updateValidationAndMalformedInputLeaveProductUnchanged() throws Exception {
        Category category = category(true);
        Product product = products.saveAndFlush(Product.builder().category(category)
                .nombre("Original").descripcion("Antes").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).activo(true).destacado(false).build());
        Cookie access = access(Role.ADMIN);
        String item = PATH + "/" + product.getId();
        String valid = body(category.getId(), "Arena", "12.50", true);
        String[] invalid = {
                valid.replace("\"Arena\"", "null"),
                valid.replace("\"Arena\"", "\"   \""),
                valid.replace("\"Arena\"", "\"" + "x".repeat(161) + "\""),
                valid.replace("\"categoriaId\":" + category.getId(), "\"categoriaId\":null"),
                valid.replace("\"categoriaId\":" + category.getId(), "\"categoriaId\":0"),
                valid.replace("\"descripcion\":\"Material\"", "\"descripcion\":\"" + "x".repeat(1001) + "\""),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":null"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":0"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":-1"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":12.501"),
                valid.replace("\"precioReferencia\":12.50", "\"precioReferencia\":12345678901234.00"),
                valid.replace("\"disponible\":true", "\"disponible\":null")
        };
        for (String request : invalid) {
            mvc.perform(put(item).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        mvc.perform(put(item).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(put(PATH + "/invalid").cookie(access).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        Product unchanged = products.findById(product.getId()).orElseThrow();
        assertThat(products.count()).isEqualTo(1);
        assertThat(unchanged.getNombre()).isEqualTo("Original");
        assertThat(unchanged.getDescripcion()).isEqualTo("Antes");
        assertThat(unchanged.getPrecioReferencia()).isEqualByComparingTo("10.00");
        assertThat(unchanged.isDisponible()).isFalse();
        assertThat(unchanged.isActivo()).isTrue();
    }

    @Test
    void missingReferencesReturnApiErrorWithoutChangingProduct() throws Exception {
        Category category = category(true);
        Product product = products.saveAndFlush(Product.builder().category(category)
                .nombre("Original").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).activo(true).build());
        Cookie access = access(Role.USER);
        String valid = body(category.getId(), "Nuevo", "12.50", true);

        mvc.perform(put(PATH + "/99999999").cookie(access).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(put(PATH + "/" + product.getId()).cookie(access).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(99999999L, "Nuevo", "12.50", true)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        for (String action : new String[]{"deactivate", "reactivate"}) {
            mvc.perform(post(PATH + "/99999999/" + action).cookie(access).with(csrf()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        assertThat(products.count()).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getNombre()).isEqualTo("Original");
        assertThat(products.findById(product.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void editAndStatusRoutesRequireSessionCsrfAndAllowedRole() throws Exception {
        Category category = category(true);
        Product product = products.saveAndFlush(Product.builder().category(category)
                .nombre("Original").precioReferencia(new BigDecimal("10.00"))
                .disponible(false).activo(true).build());
        Cookie access = access(Role.USER);
        String item = PATH + "/" + product.getId();
        String update = body(category.getId(), "Nuevo", "12.50", true);

        mvc.perform(put(item).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(item).cookie(access).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mvc.perform(put(item).with(user("unsupported").roles("OTHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        for (String action : new String[]{"deactivate", "reactivate"}) {
            String path = item + "/" + action;
            mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(path).cookie(access)).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("unsupported").roles("OTHER")).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        Product unchanged = products.findById(product.getId()).orElseThrow();
        assertThat(products.count()).isEqualTo(1);
        assertThat(unchanged.getNombre()).isEqualTo("Original");
        assertThat(unchanged.getPrecioReferencia()).isEqualByComparingTo("10.00");
        assertThat(unchanged.isActivo()).isTrue();
    }

    @Test
    void createEditDeactivateEditInactiveAndReactivateWithInactiveCategory() throws Exception {
        Category activeCategory = category(true);
        Category inactiveCategory = categories.saveAndFlush(Category.builder().nombre("Inactiva")
                .nombreNormalizado("inactiva").icono("brick").activo(false).build());
        Cookie access = access(Role.ADMIN);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(activeCategory.getId(), "Original", "10.00", false)))
                .andExpect(status().isCreated());
        long productId = products.findAll().getFirst().getId();
        String item = PATH + "/" + productId;

        mvc.perform(put(item).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(inactiveCategory.getId(), "Intermedio", "12.50", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(inactiveCategory.getId()));
        mvc.perform(post(item + "/deactivate").cookie(access).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(put(item).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(inactiveCategory.getId(), "Final", "15.75", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(post(item + "/reactivate").cookie(access).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.nombre").value("Final"));
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(inactiveCategory.getId(), "Otra", "10.00", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        Product saved = products.findById(productId).orElseThrow();
        assertThat(products.count()).isEqualTo(1);
        assertThat(saved.getId()).isEqualTo(productId);
        assertThat(saved.getCategory().getId()).isEqualTo(inactiveCategory.getId());
        assertThat(saved.getNombre()).isEqualTo("Final");
        assertThat(saved.getPrecioReferencia()).isEqualByComparingTo("15.75");
        assertThat(saved.isDisponible()).isFalse();
        assertThat(saved.isActivo()).isTrue();
        assertThat(saved.isDestacado()).isFalse();
    }

    private Category category(boolean active) {
        return categories.saveAndFlush(Category.builder().nombre("Materiales")
                .nombreNormalizado("materiales").icono("brick").activo(active).build());
    }

    private String body(Long categoryId, String name, String price, boolean available) {
        return "{\"nombre\":\"" + name + "\",\"categoriaId\":" + categoryId
                + ",\"descripcion\":\"Material\",\"precioReferencia\":" + price
                + ",\"disponible\":" + available + "}";
    }

    private Cookie access(Role role) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(role).enabled(true).build());
        String sessionId = sessions.createSession(user).session().getId();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), sessionId));
    }
}
