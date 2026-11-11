package com.ashenox.starter.catalog.subcategory;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:subcategorycontrollertest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SubcategoryControllerTest {
    private static final String PATH = "/api/catalog/subcategories";
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired SubcategoryRepository subcategories;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;

    @BeforeEach
    void clean() {
        subcategories.deleteAll();
        categories.deleteAll();
    }

    @Test
    void crudListFilterAndFreeNameForEveryRole() throws Exception {
        Category first = category(true);
        Category second = category(true);
        for (Role role : Role.values()) {
            Cookie access = access(role);
            long parent = role == Role.USER ? second.getId() : first.getId();
            var created = mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(parent, "  Chapas  ")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(parent))
                    .andExpect(jsonPath("$.nombre").value("Chapas"))
                    .andExpect(jsonPath("$.nombreNormalizado").doesNotExist()).andReturn();
            long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                    created.getResponse().getContentAsString(), "$.id")).longValue();
            mvc.perform(get(PATH + "/" + id).cookie(access)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.productosAsociados").value(0));
            mvc.perform(put(PATH + "/" + id).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"  Láminas  \",\"descripcion\":\"Nueva\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Láminas"))
                    .andExpect(jsonPath("$.descripcion").value("Nueva"));
            mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].productosAsociados").doesNotExist());
            mvc.perform(get(PATH).param("categoriaId", Long.toString(parent)).cookie(access))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
            mvc.perform(delete(PATH + "/" + id).cookie(access).with(csrf()))
                    .andExpect(status().isNoContent());
            mvc.perform(get(PATH + "/" + id).cookie(access)).andExpect(status().isNotFound());
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(createBody(parent, "LÁMINAS")))
                    .andExpect(status().isCreated());
            subcategories.deleteAll();
        }
    }

    @Test
    void rejectsInvalidParentDuplicateAndInvalidPayload() throws Exception {
        Category active = category(true);
        Category inactive = category(false);
        Cookie access = access(Role.ADMIN);
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(999999L, "Chapas")))
                .andExpect(status().isNotFound());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(inactive.getId(), "Chapas")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(active.getId(), "Chapas")))
                .andExpect(status().isCreated());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(active.getId(), " CHAPAS ")))
                .andExpect(status().isConflict());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(inactive.getId(), "Chapas")))
                .andExpect(status().isConflict());
        for (String body : new String[]{"{}", "{\"categoriaId\":0,\"nombre\":\"X\"}",
                createBody(active.getId(), " "), createBody(active.getId(), "x".repeat(161)),
                "{\"categoriaId\":" + active.getId() + ",\"nombre\":\"X\",\"descripcion\":\"" + "x".repeat(1001) + "\"}"}) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH + "/999999").cookie(access)).andExpect(status().isNotFound());
        mvc.perform(put(PATH + "/999999").cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/999999").cookie(access).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void editCannotTakeAnotherNameUnderSameParent() throws Exception {
        Category parent = category(true);
        Cookie access = access(Role.USER);
        for (String name : new String[]{"Chapas", "Caños"}) {
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(parent.getId(), name))).andExpect(status().isCreated());
        }
        long target = subcategories.findByCategoryIdOrderByNombreAscIdAsc(parent.getId()).stream()
                .filter(item -> item.getNombre().equals("Chapas")).findFirst().orElseThrow().getId();
        mvc.perform(put(PATH + "/" + target).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" CAÑOS \"}"))
                .andExpect(status().isConflict());
        mvc.perform(put(PATH + "/" + target).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(subcategories.findById(target).orElseThrow().getCategory().getId()).isEqualTo(parent.getId());
    }

    @Test
    void routesRequireSessionRoleAndCsrf() throws Exception {
        Category parent = category(true);
        Cookie access = access(Role.USER);
        String body = createBody(parent.getId(), "Chapas");
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(user("outsider").roles("OTHER"))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        long id = subcategories.findAll().get(0).getId();
        mvc.perform(put(PATH + "/" + id).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Caños\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/" + id).cookie(access)).andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/" + id).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + "/" + id).with(user("outsider").roles("OTHER")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(subcategories.existsById(id)).isTrue();
    }

    @Test
    void hierarchyEnforcesParentDepthAndSiblingNames() throws Exception {
        Category category = category(true);
        Category other = category(true);
        Cookie access = access(Role.ADMIN);
        long root = createNode(access, category.getId(), "Raiz", null);
        long root2 = createNode(access, category.getId(), "Otra", null);
        long child = createNode(access, category.getId(), "Hijo", root);
        createNode(access, category.getId(), "Hijo", root2);
        mvc.perform(get(PATH + "/" + child).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subcategoriaPadreId").value(root));
        mvc.perform(put(PATH + "/" + child).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Hijo editado\",\"subcategoriaPadreId\":" + root2 + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subcategoriaPadreId").value(root));
        mvc.perform(put(PATH + "/" + child).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Hijo\"}")).andExpect(status().isOk());
        mvc.perform(get(PATH + "/" + root).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hijosAsociados").value(1));
        for (long invalid : new long[]{root, child}) {
            String name = invalid == root ? "HIJO" : "Nieto";
            mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"categoriaId\":" + category.getId() + ",\"nombre\":\"" + name
                            + "\",\"subcategoriaPadreId\":" + invalid + "}"))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + other.getId() + ",\"nombre\":\"X\",\"subcategoriaPadreId\":" + root + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoriaId\":" + category.getId() + ",\"nombre\":\"X\",\"subcategoriaPadreId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/" + root).cookie(access).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/catalog/categories/" + category.getId()).cookie(access).with(csrf()))
                .andExpect(status().isConflict());
        subcategories.deleteAll(subcategories.findByParentSubcategoryIdOrderByNombreAscIdAsc(root));
        subcategories.deleteAll(subcategories.findByParentSubcategoryIdOrderByNombreAscIdAsc(root2));
        mvc.perform(delete(PATH + "/" + root).cookie(access).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(PATH + "/" + root2).cookie(access).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/catalog/categories/" + category.getId()).cookie(access).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/catalog/categories/" + category.getId()).cookie(access).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryDeleteRequiresRoleAndCsrfAndAllowsInactiveEmptyCategory() throws Exception {
        Category inactive = category(false);
        String path = "/api/catalog/categories/" + inactive.getId();
        Cookie access = access(Role.USER);
        mvc.perform(delete(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).cookie(access)).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user("outsider").roles("OTHER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path).cookie(access).with(csrf())).andExpect(status().isNoContent());
    }

    private long createNode(Cookie access, long categoryId, String name, Long parentId) throws Exception {
        String body = "{\"categoriaId\":" + categoryId + ",\"nombre\":\"" + name + "\""
                + (parentId == null ? "" : ",\"subcategoriaPadreId\":" + parentId) + "}";
        var result = mvc.perform(post(PATH).cookie(access).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Category category(boolean active) {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Category " + suffix)
                .nombreNormalizado("category-" + suffix).icono("icon").activo(active).build());
    }

    private static String createBody(long parentId, String name) {
        return "{\"categoriaId\":" + parentId + ",\"nombre\":\"" + name + "\"}";
    }

    private Cookie access(Role role) {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(role).enabled(true).build());
        String sessionId = sessions.createSession(user).session().getId();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), sessionId));
    }
}
