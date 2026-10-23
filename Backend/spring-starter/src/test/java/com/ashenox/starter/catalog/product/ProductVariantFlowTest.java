package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import com.ashenox.starter.catalog.product.dto.CreateProductRequest;
import com.ashenox.starter.catalog.product.dto.ProductCharacteristicRequest;
import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import com.ashenox.starter.catalog.product.service.ProductService;
import com.ashenox.starter.catalog.product.variant.dto.CreateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.UpdateVariantRequest;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantConflictException;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import com.ashenox.starter.shared.error.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ProductVariantFlowTest {
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicService characteristics;
    @Autowired ProductService products;
    @Autowired ProductVariantService variants;
    @Autowired MockMvc mvc;

    @Test
    void gradualVariantsKeepIdsAndReserveInactiveValues() {
        Long category = category();
        Long color = characteristic("Color");
        Long size = characteristic("Tamaño");
        Long product = product(category);
        long initialId = products.getById(product).varianteInicialId();
        assertThatThrownBy(() -> variants.create(product, create(color, "Blanco", "", "X", "20")))
                .isInstanceOf(ProductVariantConflictException.class);
        variants.update(product, initialId, update(color, "Rojo", null, "A", "10"));
        long secondId = variants.create(product, create(color, "Blanco", null, "B", "12")).id();
        long thirdId = variants.create(product, create(color, "Celeste", null, "C", "14")).id();
        assertThat(variants.list(product)).extracting(value -> value.id())
                .containsExactly(initialId, secondId, thirdId);
        variants.setActive(product, secondId, false);
        assertThatThrownBy(() -> variants.create(product, create(color, " blanco ", "", "D", "15")))
                .isInstanceOf(ProductVariantConflictException.class);
        assertThatThrownBy(() -> variants.update(product, thirdId, update(size, "Grande", null, "C", "14")))
                .isInstanceOf(ProductVariantConflictException.class);
        variants.setActive(product, initialId, false);
        assertThatThrownBy(() -> variants.setActive(product, thirdId, false))
                .isInstanceOf(ProductVariantConflictException.class);
        variants.setActive(product, secondId, true);
        assertThat(products.getById(product).variantes()).hasSize(3);
        assertThat(products.getById(product).varianteInicialId()).isEqualTo(initialId);
        assertThat(products.list(0, 100, null, null, null).content().stream()
                .filter(row -> row.id().equals(product)).findFirst().orElseThrow().variantes()).hasSize(3);
    }

    @Test
    void commonAttributesAndCharacteristicDeletionRespectVariableUsage() {
        Long category = category();
        Long color = characteristic("Color");
        Long product = product(category);
        long first = products.getById(product).varianteInicialId();
        variants.update(product, first, update(color, "Rojo", null, null, "10"));
        assertThatThrownBy(() -> products.update(product, common(category,
                List.of(new ProductCharacteristicRequest(color, "Rojo", null)))))
                .isInstanceOf(ProductVariantConflictException.class);
        assertThatThrownBy(() -> characteristics.delete(color)).isInstanceOf(ProductVariantConflictException.class);
        assertThat(characteristics.getById(color).cantidadVariantes()).isEqualTo(1);
        variants.create(product, create(color, "Blanco", null, null, "12"));
        assertThat(characteristics.getById(color).cantidadVariantes()).isEqualTo(2);
        variants.setActive(product, first, false);
        assertThat(characteristics.getById(color).cantidadVariantes()).isEqualTo(2);
        assertThat(characteristics.list().stream().filter(item -> item.id().equals(color))
                .findFirst().orElseThrow().cantidadVariantes()).isEqualTo(2);
        assertThat(characteristics.getById(color).cantidadProductos()).isZero();
    }

    @Test
    void productUpdateSeparatesLegacyAndCommonFields() {
        Long category = category();
        Long color = characteristic("Color");
        Long product = product(category);
        long first = products.getById(product).varianteInicialId();
        UpdateProductRequest legacy = new UpdateProductRequest("Nuevo", category, null,
                new BigDecimal("11"), false);
        products.update(product, legacy);
        assertThat(variants.list(product).getFirst().precioReferencia()).isEqualByComparingTo("11");
        variants.update(product, first, update(color, "Rojo", null, null, "11"));
        variants.create(product, create(color, "Blanco", null, null, "12"));
        assertThatThrownBy(() -> products.update(product, legacy)).isInstanceOf(ProductVariantConflictException.class);
        UpdateProductRequest common = common(category, List.of());
        assertThat(products.update(product, common).nombre()).isEqualTo("Común");
        assertThat(products.getById(product).precioReferencia()).isEqualByComparingTo("11");
    }

    @Test
    void nestedInitialVariantRejectsMixedSourcesAndSkuIsGlobal() {
        Long category = category();
        Long color = characteristic("Color");
        CreateVariantRequest initial = create(color, "Rojo", "L", " SKU-1 ", "13");
        assertThatThrownBy(() -> products.create(new CreateProductRequest("Mezclado", category, null,
                new BigDecimal("10"), true, List.of(), null, initial)))
                .isInstanceOf(BusinessException.class);
        long product = products.create(new CreateProductRequest("Nuevo", category, null,
                null, null, List.of(), null, initial)).id();
        assertThat(products.getById(product).variantes().getFirst().sku()).isEqualTo("SKU-1");
        assertThat(products.getById(product).precioReferencia()).isEqualByComparingTo("13");
        long another = product(category);
        assertThatThrownBy(() -> variants.update(another, products.getById(another).varianteInicialId(),
                update(null, null, null, "sku-1", "10")))
                .isInstanceOf(ProductVariantConflictException.class);
        assertThatThrownBy(() -> variants.create(product, create(color, " ROJO ", "l", null, "15")))
                .isInstanceOf(ProductVariantConflictException.class);
        assertThat(variants.create(product, create(color, "Rojo", "kg", null, "15")).id())
                .isNotEqualTo(products.getById(product).varianteInicialId());
    }

    @Test
    void httpRoutesRequireRoleAndCsrfAndExposeAdministrativeVariants() throws Exception {
        Long category = category();
        Long color = characteristic("Color");
        Long product = product(category);
        long first = products.getById(product).varianteInicialId();
        String base = "/api/catalog/products/" + product + "/variants";
        String body = "{\"precioReferencia\":12,\"disponible\":true,\"caracteristicaId\":" + color
                + ",\"valor\":\"Blanco\"}";
        mvc.perform(post(base).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(base).with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(base).with(user("other").roles("OTHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(base + "/" + first).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"precioReferencia\":10,\"disponible\":true,\"caracteristicaId\":"
                        + color + ",\"valor\":\"Rojo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first));
        mvc.perform(post(base).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.valor").value("Blanco"));
        mvc.perform(get(base).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].valor").value("Blanco"));
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "USER")) {
            mvc.perform(get(base).with(user("allowed").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(first));
            mvc.perform(post(base + "/" + first + "/reactivate")
                    .with(user("allowed").roles(role)).with(csrf())).andExpect(status().isOk());
        }
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(user("other").roles("OTHER"))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + first + "/deactivate")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post(base + "/" + first + "/reactivate")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/catalog/products/" + product).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Común\",\"categoriaId\":"
                        + category + ",\"precioReferencia\":20}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/catalog/products/" + product).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Común\",\"categoriaId\":"
                        + category + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Común"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/catalog/characteristics/" + color)
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get("/api/catalog/products/" + product).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variantes.length()").value(2));
        mvc.perform(get("/api/catalog/products").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].variantes").isArray());
    }

    @Test
    void httpProductCreationAcceptsNestedInitialVariantOnly() throws Exception {
        Long category = category();
        Long color = characteristic("Color");
        String nested = "{\"nombre\":\"Pintura\",\"categoriaId\":" + category
                + ",\"varianteInicial\":{\"sku\":\"P-ROJO\",\"precioReferencia\":19.50,"
                + "\"disponible\":true,\"caracteristicaId\":" + color + ",\"valor\":\"Rojo\"}}";
        mvc.perform(post("/api/catalog/products").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(nested))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.variantes[0].sku").value("P-ROJO"))
                .andExpect(jsonPath("$.precioReferencia").value(19.50));
        mvc.perform(post("/api/catalog/products").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(nested.replace("\"varianteInicial\"", "\"precioReferencia\":10,\"disponible\":true,\"varianteInicial\"")))
                .andExpect(status().isBadRequest());
    }

    private Long category() {
        String suffix = UUID.randomUUID().toString();
        return categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").activo(true).build()).getId();
    }

    private Long characteristic(String prefix) {
        return characteristics.create(new CreateCharacteristicRequest(prefix + UUID.randomUUID())).id();
    }

    private Long product(Long category) {
        return products.create(new CreateProductRequest("Base", category, null, new BigDecimal("10"), true)).id();
    }

    private CreateVariantRequest create(Long characteristic, String value, String unit, String sku, String price) {
        return new CreateVariantRequest(sku, new BigDecimal(price), true, characteristic, value, unit);
    }

    private UpdateVariantRequest update(Long characteristic, String value, String unit, String sku, String price) {
        return new UpdateVariantRequest(sku, new BigDecimal(price), true, characteristic, value, unit);
    }

    private UpdateProductRequest common(Long category, List<ProductCharacteristicRequest> values) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setNombre("Común");
        request.setCategoriaId(category);
        request.setCaracteristicas(values);
        return request;
    }
}
