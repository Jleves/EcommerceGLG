package com.ashenox.starter.catalog.category;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

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
class CategoryControllerTest {
    private static final String PATH = "/api/catalog/categories";

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;

    @BeforeEach
    void clean() {
        categories.deleteAll();
    }

    @Test
    void createAndListThroughDatabaseForEveryRole() throws Exception {
        for (Role role : Role.values()) {
            Cookie access = access(role);
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"  Cemento " + role + "  \",\"icono\":\"brick\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value("Cemento " + role))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.descripcion").doesNotExist())
                    .andExpect(jsonPath("$.nombreNormalizado").doesNotExist());
            mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(role.ordinal() + 1));
        }
        assertThat(categories.count()).isEqualTo(3);
    }

    @Test
    void emptyListAndValidationErrors() throws Exception {
        Cookie access = access(Role.USER);
        mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        for (String body : new String[]{
                "{\"nombre\":\"   \",\"icono\":\"brick\"}",
                "{\"nombre\":\"Cemento\",\"icono\":\" \"}",
                "{\"nombre\":\"" + "x".repeat(161) + "\",\"icono\":\"brick\"}",
                "{\"nombre\":\"Cemento\",\"icono\":\"" + "x".repeat(101) + "\"}",
                "{\"nombre\":\"Cemento\",\"icono\":\"brick\",\"descripcion\":\"" + "x".repeat(1001) + "\"}"}) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(categories.count()).isZero();
    }

    @Test
    void duplicateNamesIncludeInactiveRowsAndReturnApiError() throws Exception {
        categories.saveAndFlush(Category.builder().nombre("Cemento").nombreNormalizado("cemento")
                .icono("brick").activo(false).build());
        Cookie access = access(Role.ADMIN);
        var result = mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  CEMENTO  \",\"icono\":\"brick\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .isNotBlank();
        mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activo").value(false));
        categories.saveAndFlush(Category.builder().nombre("Arena").nombreNormalizado("arena")
                .icono("sand").activo(true).build());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" ARENA \",\"icono\":\"sand\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        assertThat(categories.count()).isEqualTo(2);
    }

    @Test
    void authenticationCsrfAndMethodAuthorizationRemainEnforced() throws Exception {
        Cookie access = access(Role.USER);
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"icono\":\"i\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"A\",\"icono\":\"i\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(user("unsupported").roles("OTHER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(access)).andExpect(status().isForbidden());
        assertThat(categories.count()).isZero();
    }

    private Cookie access(Role role) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(role).enabled(true).build());
        String sessionId = sessions.createSession(user).session().getId();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), sessionId));
    }
}
