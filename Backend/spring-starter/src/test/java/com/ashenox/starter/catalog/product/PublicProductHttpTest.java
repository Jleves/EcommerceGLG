package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;
import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristicId;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import com.ashenox.starter.catalog.product.variant.repository.VariantCharacteristicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:publicproducthttptest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicProductHttpTest {
    private static final String PATH = "/api/public/products";
    private static final String CDN = "https://test-images.cloudfront.net/";

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired VariantCharacteristicRepository variantCharacteristics;
    @Autowired CategoryRepository categories;
    @Autowired CharacteristicRepository characteristics;
    @Autowired ProductCharacteristicRepository productCharacteristics;
    @MockitoSpyBean ProductImageRepository images;

    @BeforeEach
    void clean() {
        productCharacteristics.deleteAll();
        images.deleteAll();
        variantCharacteristics.deleteAll();
        variants.deleteAll();
        products.deleteAll();
        characteristics.deleteAll();
        categories.deleteAll();
    }

    @Test
    void listSelectsCheapestAvailableThenCheapestActiveAndCountsProducts() throws Exception {
        Category category = category();
        Product available = product(category, "A", true, true);
        ProductVariant first = variants.findByProductId(available.getId()).orElseThrow();
        first.setPrecioReferencia(new BigDecimal("20.00"));
        variants.saveAndFlush(first);
        variant(available, "9.00", false, true);
        ProductVariant selected = variant(available, "15.00", true, true);
        variant(available, "15.00", true, true);
        variant(available, "1.00", true, false);
        Product unavailable = product(category, "B", true, false);
        ProductVariant fallback = variant(unavailable, "5.00", false, true);
        products.saveAndFlush(Product.builder().category(category).nombre("C sin variantes").activo(true).build());
        product(category, "D oculto", false, true);

        mvc.perform(get(PATH).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(available.getId()))
                .andExpect(jsonPath("$.content[0].selectedVariantId").value(selected.getId()))
                .andExpect(jsonPath("$.content[0].precioReferencia").value(15.00))
                .andExpect(jsonPath("$.content[0].disponible").value(true));
        mvc.perform(get(PATH).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(unavailable.getId()))
                .andExpect(jsonPath("$.content[0].selectedVariantId").value(fallback.getId()))
                .andExpect(jsonPath("$.content[0].precioReferencia").value(5.00))
                .andExpect(jsonPath("$.content[0].disponible").value(false));
    }

    @Test
    void detailExposesOnlyActiveVariantsWithSkuAndSharedGallery() throws Exception {
        Category category = category();
        Product product = product(category, "Pintura", true, true);
        ProductVariant initial = variants.findByProductId(product.getId()).orElseThrow();
        initial.setSku("ROJO");
        initial.setSkuNormalizado("rojo");
        variants.saveAndFlush(initial);
        ProductVariant hidden = variant(product, "1.00", true, false);
        Characteristic color = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Color").nombreNormalizado("color-" + UUID.randomUUID()).build());
        variantCharacteristics.saveAndFlush(VariantCharacteristic.builder().variant(initial)
                .characteristic(color).valor("Rojo").valorNormalizado("rojo").unidad("L")
                .unidadNormalizada("l").build());
        image(product, 1, true, "PUBLISHED");

        mvc.perform(get(PATH + "/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedVariantId").value(initial.getId()))
                .andExpect(jsonPath("$.variantes.length()").value(1))
                .andExpect(jsonPath("$.variantes[0].id").value(initial.getId()))
                .andExpect(jsonPath("$.variantes[0].sku").value("ROJO"))
                .andExpect(jsonPath("$.variantes[0].caracteristicaNombre").value("Color"))
                .andExpect(jsonPath("$.variantes[0].valor").value("Rojo"))
                .andExpect(jsonPath("$.variantes[0].unidad").value("L"))
                .andExpect(jsonPath("$.images.length()").value(1));
        ProductVariant alternate = variant(product, "8.00", true, true);
        mvc.perform(get(PATH + "/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedVariantId").value(alternate.getId()))
                .andExpect(jsonPath("$.images.length()").value(1));
        initial.setActivo(false);
        alternate.setActivo(false);
        variants.saveAndFlush(initial);
        variants.saveAndFlush(alternate);
        mvc.perform(get(PATH + "/" + product.getId())).andExpect(status().isNotFound());
        // La variante inactiva sigue almacenada y nunca fue publicada.
        org.assertj.core.api.Assertions.assertThat(variants.existsById(hidden.getId())).isTrue();
    }

    @Test
    void listReturnsOnlyActiveProductsAndFetchesPrimaryImagesInOneBatch() throws Exception {
        Category category = category();
        Product arena = product(category, "Arena", true, false);
        Product cemento = product(category, "Cemento", true, true);
        Product inactive = product(category, "Oculto", false, true);
        image(arena, 1, true, "PUBLISHED");
        image(arena, 2, false, "PUBLISHED");
        image(cemento, 0, false, "PENDING");
        image(inactive, 1, true, "PUBLISHED");
        clearInvocations(images);

        mvc.perform(get(PATH).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value(arena.getId()))
                .andExpect(jsonPath("$.content[0].primaryThumbnailUrl")
                        .value(CDN + key(arena, 1, "thumb")))
                .andExpect(jsonPath("$.content[0].disponible").value(false))
                .andExpect(jsonPath("$.content[1].id").value(cemento.getId()))
                .andExpect(jsonPath("$.content[1].primaryThumbnailUrl").value((Object) null))
                .andExpect(jsonPath("$.content[2]").doesNotExist());
        verify(images, times(1)).findPublishedPrimaryByProductIds(List.of(arena.getId(), cemento.getId()));
        verify(images, never()).findByProductIdAndStatusOrderByPositionAscIdAsc(arena.getId(), "PUBLISHED");
        verify(images, never()).findByProductIdAndStatusOrderByPositionAscIdAsc(cemento.getId(), "PUBLISHED");

        mvc.perform(get(PATH).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(cemento.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(PATH).param("page", "4").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(PATH).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void detailReturnsOrderedPublishedGalleryAndCharacteristicsOnlyForActiveProduct() throws Exception {
        Category category = category();
        Product product = product(category, "Arena", true, true);
        Characteristic characteristic = characteristics.saveAndFlush(Characteristic.builder()
                .nombre("Marca").nombreNormalizado("marca-" + UUID.randomUUID()).build());
        productCharacteristics.saveAndFlush(ProductCharacteristic.builder()
                .id(new ProductCharacteristicId(product.getId(), characteristic.getId()))
                .product(product).characteristic(characteristic).valor("Acme").build());
        ProductImage second = image(product, 2, true, "PUBLISHED");
        ProductImage first = image(product, 1, false, "PUBLISHED");
        image(product, 0, false, "PENDING");
        Product inactive = product(category, "Oculto", false, true);

        mvc.perform(get(PATH + "/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Arena"))
                .andExpect(jsonPath("$.caracteristicas[0].valor").value("Acme"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].id").value(first.getId()))
                .andExpect(jsonPath("$.images[0].position").value(1))
                .andExpect(jsonPath("$.images[0].thumbnailUrl").value(CDN + key(product, 1, "thumb")))
                .andExpect(jsonPath("$.images[0].detailUrl").value(CDN + key(product, 1, "detail")))
                .andExpect(jsonPath("$.images[1].id").value(second.getId()))
                .andExpect(jsonPath("$.images[1].primary").value(true));
        mvc.perform(get(PATH + "/" + inactive.getId())).andExpect(status().isNotFound());
        mvc.perform(get(PATH + "/99999999")).andExpect(status().isNotFound());
        Product empty = product(category, "Sin fotos", true, true);
        mvc.perform(get(PATH + "/" + empty.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(0));
    }

    @Test
    void publicRoutesAreReadOnlyAndAdminRoutesStillRequireSession() throws Exception {
        Product product = product(category(), "Arena", true, true);
        mvc.perform(get(PATH)).andExpect(status().isOk());
        mvc.perform(get(PATH + "/" + product.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/catalog/products/" + product.getId() + "/images"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(csrf()).with(user("visitor"))).andExpect(status().isForbidden());
        mvc.perform(put(PATH + "/" + product.getId()).with(csrf()).with(user("visitor")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/" + product.getId()).with(csrf()).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSearchCombinesTextCategoryBrandAndAttributesWithoutPublishingInactiveProducts() throws Exception {
        Category materials = category();
        Category tools = categories.saveAndFlush(Category.builder().nombre("Herramientas")
                .nombreNormalizado("herramientas-" + UUID.randomUUID()).icono("herramientas").activo(true).build());
        Product matching = product(materials, "Cemento Portland", true, true);
        Product otherBrand = product(materials, "Cemento blanco", true, true);
        Product otherCategory = product(tools, "Taladro", true, true);
        Product hidden = product(materials, "Cemento oculto", false, true);
        Characteristic brand = characteristics.saveAndFlush(Characteristic.builder().nombre("Marca")
                .nombreNormalizado("marca").build());
        Characteristic finish = characteristics.saveAndFlush(Characteristic.builder().nombre("Acabado")
                .nombreNormalizado("acabado").build());
        Characteristic color = characteristics.saveAndFlush(Characteristic.builder().nombre("Color")
                .nombreNormalizado("color").build());
        assign(matching, brand, "Acme");
        assign(matching, finish, "Gris");
        assign(otherBrand, brand, "Otra");
        assign(otherCategory, brand, "Acme");
        assign(hidden, brand, "Acme");
        ProductVariant matchingVariant = variants.findByProductId(matching.getId()).orElseThrow();
        variantCharacteristics.saveAndFlush(VariantCharacteristic.builder().variant(matchingVariant)
                .characteristic(color).valor("Azul").valorNormalizado("azul").build());

        mvc.perform(get(PATH).param("q", "cemento").param("categoriaId", materials.getId().toString())
                        .param("marca", "ACME").param("atributo", finish.getId() + ":Gris"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matching.getId()));
        mvc.perform(get(PATH).param("q", "acme"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).param("q", "azul").param("atributo", color.getId() + ":Azul"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).param("q", "cemento").param("marca", "inexistente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PATH).param("atributo", "malformado")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.facets.length()").value(3));
        mvc.perform(get(PATH + "/" + matching.getId() + "/related"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == " + matching.getId() + ")]").isEmpty());
        mvc.perform(get(PATH + "/" + hidden.getId() + "/related")).andExpect(status().isNotFound());
    }

    private void assign(Product product, Characteristic characteristic, String value) {
        productCharacteristics.saveAndFlush(ProductCharacteristic.builder()
                .id(new ProductCharacteristicId(product.getId(), characteristic.getId()))
                .product(product).characteristic(characteristic).valor(value).build());
    }

    private Category category() {
        return categories.saveAndFlush(Category.builder().nombre("Materiales")
                .nombreNormalizado("materiales-" + UUID.randomUUID()).icono("brick").activo(true).build());
    }

    private Product product(Category category, String name, boolean active, boolean available) {
        Product saved = products.saveAndFlush(Product.builder().category(category).nombre(name)
                .descripcion("Material").activo(active).build());
        variants.saveAndFlush(ProductVariant.builder().product(saved).precioReferencia(new BigDecimal("12.50"))
                .disponible(available).activo(true).build());
        return saved;
    }

    private ProductVariant variant(Product product, String price, boolean available, boolean active) {
        return variants.saveAndFlush(ProductVariant.builder().product(product)
                .precioReferencia(new BigDecimal(price)).disponible(available).activo(active).build());
    }

    private ProductImage image(Product product, int position, boolean primary, String status) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setPosition(position);
        image.setPrimary(primary);
        image.setRevision(UUID.randomUUID().toString());
        image.setStatus(status);
        image.setThumbnailKey(key(product, position, "thumb"));
        image.setDetailKey(key(product, position, "detail"));
        return images.saveAndFlush(image);
    }

    private String key(Product product, int position, String variant) {
        return "products/" + product.getId() + "/images/" + position + "/test/" + variant + ".webp";
    }
}
