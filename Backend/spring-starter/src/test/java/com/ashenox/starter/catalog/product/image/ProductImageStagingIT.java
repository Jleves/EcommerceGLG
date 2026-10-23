package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit staging smoke test; invoke with -Dtest=ProductImageStagingIT and staging environment. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageStagingIT {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired AppProperties properties;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;
    private String refreshToken;

    @AfterEach
    void revokeTestSession() {
        if (refreshToken != null) sessions.revoke(refreshToken);
    }

    @Test
    void authenticatedUploadIsDeliveredByCloudFrontButDeniedDirectlyByS3() throws Exception {
        assertThat(properties.getImages().getBucket()).contains("staging");
        Product product;
        String existingId = System.getProperty("stagingProductId");
        if (existingId == null) {
            String suffix = UUID.randomUUID().toString();
            Category category = categories.saveAndFlush(Category.builder()
                    .nombre("Tanda 1 prueba imagenes " + suffix)
                    .nombreNormalizado("tanda-1-imagenes-" + suffix).icono("test").build());
            product = products.saveAndFlush(Product.builder().category(category)
                    .nombre("Tanda 1 prueba imagenes " + suffix)
                    .descripcion("Producto de prueba que permanece hasta la tanda 2")
                    .build());
        } else {
            product = products.findById(Long.parseLong(existingId)).orElseThrow();
            assertThat(product.getNombre()).startsWith("Tanda 1 prueba imagenes ");
        }
        String path = "/api/catalog/products/" + product.getId() + "/images";
        Cookie access = userCookie();
        String existingJson = mvc.perform(get(path).cookie(access)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int previousCount = new ObjectMapper().readTree(existingJson).size();

        BufferedImage pixels = new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(pixels, "png", output);
        MockMultipartFile file = new MockMultipartFile("file", "tanda-1.png", "image/png", output.toByteArray());
        String json = mvc.perform(multipart(path).file(file).cookie(access).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode image = new ObjectMapper().readTree(json);
        assertThat(image.get("primary").asBoolean()).isEqualTo(previousCount == 0);
        mvc.perform(get(path).cookie(access))
                .andExpect(status().isOk());

        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String field : new String[]{"thumbnailUrl", "detailUrl"}) {
                URI cloudFront = URI.create(image.get(field).asText());
                HttpResponse<byte[]> delivered = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    delivered = client.send(HttpRequest.newBuilder(cloudFront)
                            .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                    if (delivered.statusCode() == 200) break;
                    Thread.sleep(1000);
                }
                assertThat(delivered).isNotNull();
                assertThat(delivered.statusCode()).isEqualTo(200);
                assertThat(delivered.headers().firstValue("content-type").orElse("")).contains("image/webp");
                assertThat(delivered.body()).startsWith("RIFF".getBytes());
                String key = cloudFront.getPath().substring(1);
                URI directS3 = URI.create("https://" + properties.getImages().getBucket()
                        + ".s3.us-east-1.amazonaws.com/" + key);
                HttpResponse<Void> denied = client.send(HttpRequest.newBuilder(directS3)
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.discarding());
                assertThat(denied.statusCode()).isEqualTo(403);
            }
        }
        System.out.println("STAGING_TEST_PRODUCT_ID=" + product.getId());
        System.out.println("STAGING_TEST_IMAGE_ID=" + image.get("id").asLong());
    }

    private Cookie userCookie() {
        String email = System.getenv("INIT_SUPERADMIN_EMAIL");
        var admin = users.findByEmail(email).orElseThrow();
        var issued = sessions.createSession(admin);
        refreshToken = issued.refreshToken();
        return new Cookie(AuthCookieService.ACCESS_TOKEN,
                jwt.generateToken(admin.getId(), issued.session().getId()));
    }
}
