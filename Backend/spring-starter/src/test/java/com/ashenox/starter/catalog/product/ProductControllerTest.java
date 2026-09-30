package com.ashenox.starter.catalog.product;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
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
