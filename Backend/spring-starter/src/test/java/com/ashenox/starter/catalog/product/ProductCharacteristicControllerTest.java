package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductCharacteristicControllerTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductCharacteristicRepository values;
    @Autowired CharacteristicRepository characteristics;

    @AfterEach
    void clearAssociations() {
        values.deleteAll();
    }

    @Test
    void createsDefinitionAndTwoProductsWithDifferentValues() throws Exception {
        Category category = category();
        String definition = "Marca " + UUID.randomUUID();
        String response = mvc.perform(post("/api/catalog/characteristics").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + definition + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        for (String value : new String[]{"Acme", "Otra"}) {
            mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body(category.getId(), id, value)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.caracteristicas[0].caracteristicaId").value(id))
                    .andExpect(jsonPath("$.caracteristicas[0].nombre").value(definition))
                    .andExpect(jsonPath("$.caracteristicas[0].valor").value(value))
                    .andExpect(jsonPath("$.caracteristicas[0].unidad").doesNotExist());
        }
        assertThat(values.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void invalidReferencesAndValuesDoNotWriteProducts() throws Exception {
        Category category = category();
        String suffix = UUID.randomUUID().toString();
        long characteristicId = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Marca " + suffix).nombreNormalizado("marca-" + suffix).build()).getId();
        long before = products.count();
        String basic = "{\"nombre\":\"Arena\",\"categoriaId\":" + category.getId()
                + ",\"precioReferencia\":10.00,\"disponible\":true,";
        for (String item : new String[]{"{\"caracteristicaId\":99999999,\"valor\":\"A\"}",
                "{\"caracteristicaId\":1,\"valor\":\"   \"}",
                "{\"caracteristicaId\":null,\"valor\":\"A\"}",
                "{\"caracteristicaId\":" + characteristicId + ",\"valor\":\"" + "x".repeat(501) + "\"}",
                "{\"caracteristicaId\":" + characteristicId + ",\"valor\":\"A\",\"unidad\":\"" + "x".repeat(101) + "\"}"}) {
            mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(basic + "\"caracteristicas\":[" + item + "]}"))
                    .andExpect(item.contains("99999999") ? status().isNotFound() : status().isBadRequest());
        }
        mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        String duplicated = basic + "\"caracteristicas\":[{\"caracteristicaId\":" + characteristicId
                + ",\"valor\":\"A\"},{\"caracteristicaId\":" + characteristicId
                + ",\"valor\":\"B\"}]}";
        mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(duplicated))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(products.count()).isEqualTo(before);
    }

    @Test
    void basicProductResponseAlwaysIncludesEmptyCharacteristicArray() throws Exception {
        Category category = category();
        mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Arena\",\"categoriaId\":" + category.getId()
                                + ",\"precioReferencia\":10.00,\"disponible\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.caracteristicas").isArray())
                .andExpect(jsonPath("$.caracteristicas").isEmpty());
    }

    @Test
    void authenticationRoleAndCsrfGuardCharacteristicProductCreate() throws Exception {
        Category category = category();
        String body = body(category.getId(), 1L, "Acme");
        mvc.perform(post("/api/catalog/products").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/catalog/products").with(user("other").roles("OTHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
    }

    private String body(Long categoryId, Long characteristicId, String value) {
        return "{\"nombre\":\"Arena\",\"categoriaId\":" + categoryId
                + ",\"precioReferencia\":10.00,\"disponible\":true,\"caracteristicas\":[{\"caracteristicaId\":"
                + characteristicId + ",\"valor\":\"" + value + "\"}]}";
    }
}
