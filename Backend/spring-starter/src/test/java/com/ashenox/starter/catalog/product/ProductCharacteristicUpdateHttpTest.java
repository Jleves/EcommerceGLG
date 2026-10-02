package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import org.junit.jupiter.api.AfterEach;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCharacteristicUpdateHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ProductService products;
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductCharacteristicRepository values;

    @AfterEach
    void clearAssociations() {
        values.deleteAll();
    }

    @Test
    void omittedNullAndEmptyArrayHaveDistinctMeaning() throws Exception {
        long category = category();
        long characteristic = definition();
        long product = product(category, characteristic);
        String path = "/api/catalog/products/" + product;
        String basic = basic(category);
        mvc.perform(put(path).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(basic + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caracteristicas[0].valor").value("Original"));
        mvc.perform(put(path).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(basic + ",\"caracteristicas\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caracteristicas[0].valor").value("Original"));
        mvc.perform(put(path).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(basic + ",\"caracteristicas\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caracteristicas").isEmpty());
        mvc.perform(get(path).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caracteristicas").isEmpty());
    }

    @Test
    void invalidItemsAndSecurityLeaveProductUntouched() throws Exception {
        long category = category();
        long characteristic = definition();
        long product = product(category, characteristic);
        String path = "/api/catalog/products/" + product;
        String basic = basic(category);
        for (String item : List.of("null", "{\"caracteristicaId\":0,\"valor\":\"A\"}",
                "{\"caracteristicaId\":" + characteristic + ",\"valor\":\"   \"}",
                "{\"caracteristicaId\":" + characteristic + ",\"valor\":\"A\",\"unidad\":\"" + "x".repeat(101) + "\"}")) {
            mvc.perform(put(path).with(user("editor").roles("USER")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(basic + ",\"caracteristicas\":[" + item + "]}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(path).with(user("editor").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(basic + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(basic + "}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user("other").roles("OTHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(basic + "}"))
                .andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "USER")) {
            mvc.perform(put(path).with(user("editor").roles(role)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(basic + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get(path).with(user("editor").roles("USER")))
                .andExpect(jsonPath("$.caracteristicas[0].valor").value("Original"));
    }

    private long category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build()).getId();
    }

    private long definition() {
        String suffix = UUID.randomUUID().toString();
        return characteristics.saveAndFlush(Characteristic.builder().nombre("Marca " + suffix)
                .nombreNormalizado(suffix).build()).getId();
    }

    private long product(long category, long characteristic) {
        return products.create(new CreateProductRequest("Original", category, null,
                new BigDecimal("10"), true, List.of(new ProductCharacteristicRequest(characteristic, "Original", "kg")))).id();
    }

    private String basic(long category) {
        return "{\"nombre\":\"Actualizado\",\"categoriaId\":" + category
                + ",\"precioReferencia\":20,\"disponible\":false";
    }
}
