package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import com.ashenox.starter.catalog.product.image.storage.ImageStorageCleanupJob;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit staging lifecycle check. Run only with the staging DB, AWS credentials and admin email. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageLifecycleStagingIT {
    private static final long PRODUCT_ID = 5L;
    private static final String PATH = "/api/catalog/products/" + PRODUCT_ID + "/images";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductImageService images;
    @Autowired ImageStorageOperationRepository operations;
    @Autowired ImageStorageCleanupJob cleanup;
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
    void manageAndRemoveTandaOnePhotosThroughAuthenticatedApi() throws Exception {
        assertThat(properties.getImages().getBucket()).contains("staging");
        assertThat(products.findById(PRODUCT_ID).orElseThrow().getNombre())
                .startsWith("Tanda 1 prueba imagenes ");
        Cookie access = userCookie();
        JsonNode before = gallery(access);
        if (before.isEmpty()) {
            assertThat(images.list(PRODUCT_ID)).isEmpty();
            for (long imageId : new long[]{1L, 2L}) {
                var last = operations.findAll().stream().filter(operation -> operation.getImageId() == imageId)
                        .max(Comparator.comparing(com.ashenox.starter.catalog.product.image.model.ImageStorageOperation::getId))
                        .orElseThrow();
                assertThat(last.getStatus()).isEqualTo("COMPLETE");
            }
            return;
        }
        assertThat(before.size()).isEqualTo(2);
        assertThat(before.get(0).get("id").asLong()).isEqualTo(1L);
        assertThat(before.get(1).get("id").asLong()).isEqualTo(2L);

        mvc.perform(put(PATH + "/order").cookie(access).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[2,1]}"))
                .andExpect(status().isOk());
        mvc.perform(put(PATH + "/2/primary").cookie(access).with(csrf()))
                .andExpect(status().isOk());
        assertThat(gallery(access).get(0).get("primary").asBoolean()).isTrue();

        JsonNode replaced = mapper.readTree(mvc.perform(multipart(PATH + "/2")
                        .file(file()).cookie(access).with(csrf())
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String oldThumb = before.get(1).get("thumbnailUrl").asText();
        assertThat(replaced.get("thumbnailUrl").asText()).isNotEqualTo(oldThumb);
        assertCloudFrontWebp(replaced.get("thumbnailUrl").asText());
        assertCloudFrontWebp(replaced.get("detailUrl").asText());
        assertThat(operations.findAll().stream().filter(operation -> operation.getImageId() == 2L
                && "CLEANUP_REQUIRED".equals(operation.getStatus())
                && oldThumb.endsWith(operation.getThumbnailKey())).anyMatch(operation ->
                operation.getNextExecutionAt().isAfter(LocalDateTime.now().plusHours(24)))).isTrue();

        mvc.perform(delete(PATH + "/2").cookie(access).with(csrf())).andExpect(status().isNoContent());
        assertThat(gallery(access).size()).isEqualTo(1);
        assertThat(gallery(access).get(0).get("primary").asBoolean()).isTrue();
        mvc.perform(delete(PATH + "/1").cookie(access).with(csrf())).andExpect(status().isNoContent());
        assertThat(gallery(access).size()).isZero();

        String replacedThumb = replaced.get("thumbnailUrl").asText();
        List<Long> deletionIds = operations.findAll().stream().filter(operation ->
                "CLEANUP_REQUIRED".equals(operation.getStatus())
                && !operation.getNextExecutionAt().isAfter(LocalDateTime.now())
                && (replacedThumb.endsWith(operation.getThumbnailKey())
                || before.get(0).get("thumbnailUrl").asText().endsWith(operation.getThumbnailKey())))
                .map(com.ashenox.starter.catalog.product.image.model.ImageStorageOperation::getId).toList();
        assertThat(deletionIds).hasSize(2);
        cleanup.run();
        for (Long id : deletionIds) assertThat(operations.findById(id).orElseThrow().getStatus()).isEqualTo("COMPLETE");
        assertThat(images.list(PRODUCT_ID)).isEmpty();
    }

    private JsonNode gallery(Cookie access) throws Exception {
        return mapper.readTree(mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Cookie userCookie() {
        String email = System.getenv("INIT_SUPERADMIN_EMAIL");
        var admin = users.findByEmail(email).orElseThrow();
        var issued = sessions.createSession(admin);
        refreshToken = issued.refreshToken();
        return new Cookie(AuthCookieService.ACCESS_TOKEN,
                jwt.generateToken(admin.getId(), issued.session().getId()));
    }

    private MockMultipartFile file() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 320, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "tanda-2.png", "image/png", out.toByteArray());
    }

    private void assertCloudFrontWebp(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("image/webp");
            assertThat(response.body()).startsWith("RIFF".getBytes());
        }
    }
}
