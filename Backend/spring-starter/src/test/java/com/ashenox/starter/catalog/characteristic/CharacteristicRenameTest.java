package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.dto.UpdateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacteristicRenameTest {
    private static final String PATH = "/api/catalog/characteristics/";

    @Autowired CharacteristicService characteristics;
    @Autowired CharacteristicRepository repository;
    @Autowired CategoryRepository categories;
    @Autowired ProductService products;
    @Autowired MockMvc mvc;

    @Test
    void renamePreservesIdsAndValuesAndUpdatesEveryRead() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").build());
        long id = characteristics.create(new CreateCharacteristicRequest("Marca " + suffix)).id();
        long first = product(category, id, "Acme", "kg");
        long second = product(category, id, "Otra", null);
        products.deactivate(second);

        var updated = characteristics.update(id, new UpdateCharacteristicRequest("  NUEVA " + suffix + "  "));
        assertThat(updated.id()).isEqualTo(id);
        assertThat(updated.nombre()).isEqualTo("NUEVA " + suffix);
        assertThat(updated.cantidadProductos()).isEqualTo(2);
        assertThat(repository.findById(id).orElseThrow().getNombreNormalizado()).isEqualTo("nueva " + suffix);
        assertThat(characteristics.getById(id).nombre()).isEqualTo(updated.nombre());
        assertThat(characteristics.list()).filteredOn(item -> item.id().equals(id))
                .singleElement().extracting(item -> item.nombre()).isEqualTo(updated.nombre());
        assertThat(products.getById(first).caracteristicas()).singleElement()
                .satisfies(value -> {
                    assertThat(value.caracteristicaId()).isEqualTo(id);
                    assertThat(value.nombre()).isEqualTo(updated.nombre());
                    assertThat(value.valor()).isEqualTo("Acme");
                    assertThat(value.unidad()).isEqualTo("kg");
                });
        assertThat(products.getById(second).caracteristicas()).singleElement()
                .satisfies(value -> {
                    assertThat(value.nombre()).isEqualTo(updated.nombre());
                    assertThat(value.valor()).isEqualTo("Otra");
                    assertThat(value.unidad()).isNull();
                });
        mvc.perform(get(PATH + id).with(user("reader").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(updated.nombre()))
                .andExpect(jsonPath("$.cantidadProductos").value(2));

        var recased = characteristics.update(id, new UpdateCharacteristicRequest("nueva " + suffix));
        assertThat(recased.id()).isEqualTo(id);
        assertThat(recased.nombre()).isEqualTo("nueva " + suffix);
        assertThat(recased.cantidadProductos()).isEqualTo(2);
    }

    @Test
    void httpValidationConflictAndMissingIdReturnApiErrors() throws Exception {
        long first = characteristics.create(new CreateCharacteristicRequest("Primera " + UUID.randomUUID())).id();
        String existing = "Segunda " + UUID.randomUUID();
        characteristics.create(new CreateCharacteristicRequest(existing));
        for (String body : new String[]{"{\"nombre\":\"   \"}", "{\"nombre\":\"" + "x".repeat(161) + "\"}"}) {
            mvc.perform(put(PATH + first).with(user("editor").roles("USER")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(put(PATH + first).with(user("editor").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  " + existing.toLowerCase() + "  \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        mvc.perform(put(PATH + "999999999").with(user("editor").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(characteristics.getById(first).nombre()).startsWith("Primera ");
    }

    @Test
    void everyCatalogRoleCanRenameAndCsrfAndAuthenticationAreRequired() throws Exception {
        long id = characteristics.create(new CreateCharacteristicRequest("Inicial " + UUID.randomUUID())).id();
        for (String role : new String[]{"SUPER_ADMIN", "ADMIN", "USER"}) {
            String name = "Renombrada " + role + " " + UUID.randomUUID();
            mvc.perform(put(PATH + id).with(user("editor").roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + name + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.nombre").value(name))
                    .andExpect(jsonPath("$.cantidadProductos").value(0));
        }
        String body = "{\"nombre\":\"Prohibido\"}";
        mvc.perform(put(PATH + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PATH + id).with(user("editor").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(PATH + id).with(user("other").roles("OTHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(characteristics.getById(id).nombre()).doesNotContain("Prohibido");
    }

    private long product(Category category, long id, String value, String unit) {
        return products.create(new CreateProductRequest("Arena", category.getId(), null,
                new BigDecimal("10.00"), true,
                List.of(new ProductCharacteristicRequest(id, value, unit)))).id();
    }
}
