package com.ashenox.starter.catalog.product;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductListingTest {
    private static final String PATH = "/api/catalog/products";

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;
    @MockitoSpyBean ProductCharacteristicRepository characteristics;

    @BeforeEach
    void clean() {
        variants.deleteAll();
        products.deleteAll();
        categories.deleteAll();
    }

    @Test
    void emptyFullAndOutOfRangePagesHaveStableMetadataAndSummaryFields() throws Exception {
        Cookie access = access(Role.ADMIN);
        mvc.perform(get(PATH).cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        Category category = category();
        Product first = product(category, "Arena", true);
        Product second = product(category, "Arena", false);
        product(category, "Cemento", true);

        mvc.perform(get(PATH).cookie(access).param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.content[0].varianteInicialId").isNumber())
                .andExpect(jsonPath("$.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.content[0].categoriaId").value(category.getId()))
                .andExpect(jsonPath("$.content[0].precioReferencia").value(12.50))
                .andExpect(jsonPath("$.content[0].disponible").value(true))
                .andExpect(jsonPath("$.content[0].activo").value(true))
                .andExpect(jsonPath("$.content[1].activo").value(false))
                .andExpect(jsonPath("$.content[0].destacado").value(false))
                .andExpect(jsonPath("$.content[0].caracteristicas").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(PATH).cookie(access).param("page", "5").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(5)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        verify(characteristics, never()).findByProductIdWithCharacteristic(anyLong());
    }

    @Test
    void searchTrimsIgnoresCaseAndTreatsWildcardsLiterally() throws Exception {
        Category category = category();
        product(category, "arena fina", true);
        product(category, "A%_! material", true);
        product(category, "Cemento", false);
        Cookie access = access(Role.USER);
        mvc.perform(get(PATH).cookie(access).param("nombre", "  ArEnA  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("arena fina"));
        for (String literal : new String[]{"%", "_", "!"}) {
            mvc.perform(get(PATH).cookie(access).param("nombre", literal))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].nombre").value("A%_! material"));
        }
        mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void validatesParametersAndRequiresAllowedRole() throws Exception {
        Category category = category();
        product(category, "Arena", true);
        for (Role role : new Role[]{Role.SUPER_ADMIN, Role.ADMIN, Role.USER}) {
            mvc.perform(get(PATH).cookie(access(role))).andExpect(status().isOk());
        }
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("other").roles("OTHER"))).andExpect(status().isForbidden());
        Cookie access = access(Role.ADMIN);
        for (String name : new String[]{"", "   "}) {
            mvc.perform(get(PATH).cookie(access).param("nombre", name)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String[] params : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "101"}}) {
            mvc.perform(get(PATH).cookie(access).param(params[0], params[1])).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(PATH).cookie(access).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1));
        mvc.perform(get(PATH).cookie(access).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    private Category category() {
        return categories.saveAndFlush(Category.builder().nombre("Materiales").nombreNormalizado("materiales")
                .icono("brick").activo(true).build());
    }

    private Product product(Category category, String name, boolean active) {
        Product saved = products.saveAndFlush(Product.builder().category(category).nombre(name).descripcion("Material")
                .activo(active).destacado(false).build());
        variants.saveAndFlush(ProductVariant.builder().product(saved).precioReferencia(new BigDecimal("12.50"))
                .disponible(true).activo(true).build());
        return saved;
    }

    private Cookie access(Role role) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(role).enabled(true).build());
        String sessionId = sessions.createSession(user).session().getId();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), sessionId));
    }
}
