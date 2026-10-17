package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:productimagehttptest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@Import(ProductImageFlowTest.FakeStorageConfig.class)
class ProductImageControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired ProductImageFlowTest.FakeStorage storage;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void galleryMutationsRequireSessionRoleAndCsrf() throws Exception {
        Long id = product();
        String path = "/api/catalog/products/" + id + "/images";
        MvcResult uploaded = mvc.perform(multipart(path).file(file()).with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        Long imageId = mapper.readTree(uploaded.getResponse().getContentAsString()).get("id").longValue();
        String primary = path + "/" + imageId + "/primary";
        mvc.perform(put(primary).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put(primary).with(user("other").roles("OTHER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put(primary).with(user("editor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(put(primary).with(user("editor").roles("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.primary").value(true));
        mvc.perform(put(path + "/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[" + imageId + "]}")
                        .with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(imageId));
        mvc.perform(multipart(path + "/" + imageId).file(file())
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(user("editor").roles("SUPER_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(imageId));
        mvc.perform(delete(path + "/" + imageId).with(user("editor").roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void orderRejectsMissingAndDuplicateIds() throws Exception {
        Long id = product();
        String path = "/api/catalog/products/" + id + "/images";
        Long first = uploadId(path);
        Long second = uploadId(path);
        for (String body : new String[]{"{\"imageIds\":[" + first + "]}",
                "{\"imageIds\":[" + first + "," + first + "]}"}) {
            mvc.perform(put(path + "/order").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(user("editor").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(path + "/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[" + second + "," + first + "]}")
                        .with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(second));
    }

    private Long uploadId(String path) throws Exception {
        MvcResult result = mvc.perform(multipart(path).file(file()).with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").longValue();
    }

    @Test
    void uploadRequiresSessionRoleAndCsrf() throws Exception {
        Long id = product();
        String path = "/api/catalog/products/" + id + "/images";
        mvc.perform(multipart(path).file(file()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(file()).with(user("other").roles("OTHER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(file()).with(user("editor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(path).with(user("editor").roles("USER")).with(csrf()))
                .andExpect(status().isBadRequest());
        for (String role : new String[]{"USER", "ADMIN", "SUPER_ADMIN"}) {
            mvc.perform(multipart(path).file(file()).with(user("editor").roles(role)).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.thumbnailUrl").isString())
                    .andExpect(jsonPath("$.detailUrl").isString());
        }
        mvc.perform(get(path).with(user("editor").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void invalidFileFullGalleryAndStorageFailureHaveDocumentedStatuses() throws Exception {
        Long id = product();
        String path = "/api/catalog/products/" + id + "/images";
        mvc.perform(multipart(path).file(new MockMultipartFile("file", "fake.png", "image/png", new byte[]{1, 2, 3}))
                        .with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest());
        storage.failOnPut.set(storage.putCount.get() + 1);
        try {
            mvc.perform(multipart(path).file(file()).with(user("editor").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            storage.failOnPut.set(0);
        }
        for (int i = 0; i < 5; i++) {
            mvc.perform(multipart(path).file(file()).with(user("editor").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isCreated());
        }
        mvc.perform(multipart(path).file(file()).with(user("editor").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict());
    }

    private Long product() {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("HTTP " + suffix)
                .nombreNormalizado("http-" + suffix).icono("test").build());
        return products.saveAndFlush(Product.builder().category(category).nombre("HTTP " + suffix)
                .precioReferencia(BigDecimal.ONE).disponible(true).build()).getId();
    }

    private MockMultipartFile file() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "test.png", "image/png", out.toByteArray());
    }
}
