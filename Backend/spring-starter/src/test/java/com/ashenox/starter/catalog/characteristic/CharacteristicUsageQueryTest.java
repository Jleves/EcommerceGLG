package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristicId;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
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
class CharacteristicUsageQueryTest {
    @Autowired CharacteristicService characteristics;
    @Autowired ProductService products;
    @Autowired CategoryRepository categories;
    @Autowired ProductCharacteristicRepository values;
    @Autowired MockMvc mvc;

    @Test
    void countsProductsIncludingInactiveAndReflectsAssociationRemoval() throws Exception {
        Category category = category();
        long brandId = characteristics.create(new CreateCharacteristicRequest("Marca " + UUID.randomUUID())).id();
        long unusedId = characteristics.create(new CreateCharacteristicRequest("Vacía " + UUID.randomUUID())).id();
        assertThat(characteristics.getById(brandId).cantidadProductos()).isZero();
        assertThat(characteristics.getById(unusedId).cantidadProductos()).isZero();

        long firstId = products.create(request(category.getId(), brandId, "Acme", "kg")).id();
        long secondId = products.create(request(category.getId(), brandId, "Otra", null)).id();
        products.deactivate(secondId);

        assertThat(characteristics.getById(brandId).cantidadProductos()).isEqualTo(2);
        assertThat(characteristics.list()).filteredOn(item -> item.id().equals(brandId))
                .singleElement().extracting(item -> item.cantidadProductos()).isEqualTo(2L);
        assertThat(characteristics.list()).filteredOn(item -> item.id().equals(unusedId))
                .singleElement().extracting(item -> item.cantidadProductos()).isEqualTo(0L);
        mvc.perform(get("/api/catalog/characteristics/" + brandId).with(user("reader").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadProductos").value(2));
        mvc.perform(get("/api/catalog/characteristics").with(user("reader").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + brandId + ")].cantidadProductos").value(2));

        values.deleteById(new ProductCharacteristicId(firstId, brandId));
        assertThat(characteristics.getById(brandId).cantidadProductos()).isEqualTo(1);
        assertThat(products.getById(secondId).caracteristicas()).hasSize(1);
    }

    @Test
    void productDetailPreloadsValuesForEveryRoleAndRejectsUnauthorizedReads() throws Exception {
        Category category = category();
        long brandId = characteristics.create(new CreateCharacteristicRequest("Marca " + UUID.randomUUID())).id();
        long productId = products.create(request(category.getId(), brandId, "Acme", "kg")).id();
        products.deactivate(productId);
        String path = "/api/catalog/products/" + productId;

        for (String role : new String[]{"SUPER_ADMIN", "ADMIN", "USER"}) {
            mvc.perform(get(path).with(user("reader").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false))
                    .andExpect(jsonPath("$.caracteristicas[0].caracteristicaId").value(brandId))
                    .andExpect(jsonPath("$.caracteristicas[0].valor").value("Acme"))
                    .andExpect(jsonPath("$.caracteristicas[0].unidad").value("kg"));
        }
        mvc.perform(get("/api/catalog/products/999999999").with(user("reader").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("other").roles("OTHER"))).andExpect(status().isForbidden());
        assertThat(products.getById(productId).caracteristicas().getFirst().nombre())
                .isEqualTo(characteristics.getById(brandId).nombre());
    }

    @Test
    void characteristicCreationStillReturnsZeroCount() throws Exception {
        mvc.perform(post("/api/catalog/characteristics").with(user("editor").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Marca " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cantidadProductos").value(0));
    }

    private Category category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado("categoria-" + suffix).icono("icon").build());
    }

    private CreateProductRequest request(Long categoryId, long characteristicId, String value, String unit) {
        return new CreateProductRequest("Arena", categoryId, null, new BigDecimal("10.00"), true,
                List.of(new ProductCharacteristicRequest(characteristicId, value, unit)));
    }
}
