package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

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
class ProductSubcategoryHttpTest {
    private static final String PATH = "/api/catalog/products";

    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired SubcategoryRepository subcategories;

    @Test
    void createReadAndUpdateDistinguishOmittedNullAndNewAssignment() throws Exception {
        Category first = category(true);
        Category second = category(true);
        Subcategory firstSub = subcategory(first);
        Subcategory secondSub = subcategory(second);

        long id = create(first.getId(), firstSub.getId());
        mvc.perform(get(PATH + "/" + id).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(first.getId()))
                .andExpect(jsonPath("$.subcategoriaId").value(firstSub.getId()));
        update(id, first.getId(), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.subcategoriaId").value(firstSub.getId()));
        update(id, second.getId(), "").andExpect(status().isBadRequest());
        update(id, second.getId(), ",\"subcategoriaId\":null").andExpect(status().isOk())
                .andExpect(jsonPath("$.subcategoriaId").value(org.hamcrest.Matchers.nullValue()));
        update(id, second.getId(), ",\"subcategoriaId\":" + secondSub.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.subcategoriaId").value(secondSub.getId()));
        update(id, first.getId(), ",\"subcategoriaId\":" + firstSub.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.subcategoriaId").value(firstSub.getId()));

        long unclassified = create(first.getId(), null);
        mvc.perform(get(PATH + "/" + unclassified).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subcategoriaId")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsMissingMismatchedAndInactiveParentOnAssignment() throws Exception {
        Category active = category(true);
        Category other = category(true);
        Category inactive = category(false);
        Subcategory foreign = subcategory(other);
        Subcategory inactiveSub = subcategory(inactive);
        long id = create(active.getId(), null);

        for (long subcategoryId : new long[]{foreign.getId(), inactiveSub.getId()}) {
            mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body(active.getId(), subcategoryId)))
                    .andExpect(status().isBadRequest());
            update(id, active.getId(), ",\"subcategoriaId\":" + subcategoryId)
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(inactive.getId(), inactiveSub.getId())))
                .andExpect(status().isConflict());
        update(id, inactive.getId(), ",\"subcategoriaId\":" + inactiveSub.getId())
                .andExpect(status().isConflict());
        update(id, active.getId(), ",\"subcategoriaId\":99999999")
                .andExpect(status().isNotFound());
        mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(active.getId(), 99999999L)))
                .andExpect(status().isNotFound());
        update(id, active.getId(), ",\"subcategoriaId\":-1")
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/" + id).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subcategoriaId")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void combinesFiltersAndKeepsPaginationAndSummaryIds() throws Exception {
        Category first = category(true);
        Category second = category(true);
        Subcategory selected = subcategory(first);
        Subcategory other = subcategory(first);
        create(first.getId(), selected.getId());
        create(first.getId(), selected.getId());
        create(first.getId(), other.getId());
        create(second.getId(), null);

        mvc.perform(get(PATH).with(user("editor").roles("USER"))
                        .param("categoriaId", first.getId().toString())
                        .param("subcategoriaId", selected.getId().toString())
                        .param("nombre", "Producto").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].categoriaId").value(first.getId()))
                .andExpect(jsonPath("$.content[0].subcategoriaId").value(selected.getId()));
        mvc.perform(get(PATH).with(user("editor").roles("USER"))
                        .param("subcategoriaId", selected.getId().toString())
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(user("editor").roles("USER"))
                        .param("categoriaId", second.getId().toString())
                        .param("subcategoriaId", selected.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user("editor").roles("USER"))
                        .param("categoriaId", "-1"))
                .andExpect(status().isBadRequest());
    }

    private long create(Long categoryId, Long subcategoryId) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(mvc.perform(post(PATH)
                .with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(categoryId, subcategoryId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions update(long id, Long categoryId, String extra)
            throws Exception {
        return mvc.perform(put(PATH + "/" + id).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(categoryId, null, extra)));
    }

    private String body(Long categoryId, Long subcategoryId) {
        return body(categoryId, subcategoryId, subcategoryId == null ? "" : ",\"subcategoriaId\":" + subcategoryId);
    }

    private String body(Long categoryId, Long ignored, String extra) {
        return "{\"nombre\":\"Producto\",\"categoriaId\":" + categoryId
                + ",\"precioReferencia\":12.50,\"disponible\":true" + extra + "}";
    }

    private Category category(boolean active) {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").activo(active).build());
    }

    private Subcategory subcategory(Category category) {
        String suffix = UUID.randomUUID().toString();
        return subcategories.saveAndFlush(Subcategory.builder().category(category)
                .nombre("Sub " + suffix).nombreNormalizado("sub-" + suffix).build());
    }
}
