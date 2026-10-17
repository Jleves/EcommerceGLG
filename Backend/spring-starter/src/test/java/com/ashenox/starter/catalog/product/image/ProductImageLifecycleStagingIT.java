package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.product.image.repository.ImageStorageOperationRepository;
import com.ashenox.starter.catalog.product.image.repository.ProductImageRepository;
import com.ashenox.starter.catalog.product.image.storage.ImageStorageCleanupJob;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sksamuel.scrimage.ImmutableImage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit staging check. Run only with the staging DB, AWS credentials and admin email. */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageLifecycleStagingIT {
    private static final long PRODUCT_ID = 5L;
    private static final String PATH = "/api/catalog/products/" + PRODUCT_ID + "/images";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
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
    void galleryLifecycleWithoutReplacement() throws Exception {
        assertThat(properties.getImages().getBucket()).contains("staging");
        var product = products.findById(PRODUCT_ID).orElseThrow();
        assertThat(product.getNombre()).startsWith("Tanda 1 prueba imagenes ");
        assertThat(product.isActivo()).isTrue();
        Cookie access = userCookie();
        assertThat(gallery(access).size()).isZero();
        assertThat(images.countByProductId(PRODUCT_ID)).isZero();
        System.out.println("STAGING_TANDA_5_INITIAL_GALLERY=0 PRODUCT_ID=" + PRODUCT_ID);

        List<Long> presentIds = new ArrayList<>();
        List<Long> allIds = new ArrayList<>();
        try {
            for (int index = 0; index < 5; index++) {
                JsonNode uploaded = upload(access);
                long id = uploaded.get("id").asLong();
                presentIds.add(id);
                allIds.add(id);
                assertThat(uploaded.get("position").asInt()).isEqualTo(index + 1);
                assertThat(uploaded.get("primary").asBoolean()).isEqualTo(index == 0);
                assertCloudFrontWebp(uploaded.get("thumbnailUrl").asText(), 400);
                assertCloudFrontWebp(uploaded.get("detailUrl").asText(), 1200);
            }
            mvc.perform(multipart(PATH).file(file()).cookie(access).with(csrf()))
                    .andExpect(status().isConflict());
            assertThat(gallery(access).size()).isEqualTo(5);

            long selected = presentIds.get(2);
            mvc.perform(put(PATH + "/" + selected + "/primary").cookie(access).with(csrf()))
                    .andExpect(status().isOk());
            assertPublicPrimary(selected, access);

            List<Long> order = List.of(presentIds.get(4), presentIds.get(0), presentIds.get(1),
                    presentIds.get(2), presentIds.get(3));
            mvc.perform(put(PATH + "/order").cookie(access).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("imageIds", order))))
                    .andExpect(status().isOk());
            assertGallery(access, order, 3);

            mvc.perform(delete(PATH + "/" + selected).cookie(access).with(csrf()))
                    .andExpect(status().isNoContent());
            presentIds.remove(selected);
            assertGallery(access, order.stream().filter(id -> id != selected).toList(), 0);
            assertPublicPrimary(order.getFirst(), access);
            assertThat(gallery(access).size()).isEqualTo(4);

            JsonNode optional = upload(access);
            presentIds.add(optional.get("id").asLong());
            allIds.add(optional.get("id").asLong());
            assertThat(gallery(access).size()).isEqualTo(5);
            System.out.println("STAGING_TANDA_5_GALLERY_CHECK=PASS PRODUCT_ID=" + PRODUCT_ID);
        } finally {
            for (Long id : presentIds) {
                mvc.perform(delete(PATH + "/" + id).cookie(access).with(csrf()))
                        .andExpect(status().isNoContent());
            }
            cleanup.run();
        }
        assertThat(gallery(access).size()).isZero();
        assertThat(operations.findAll().stream().filter(operation ->
                operation.getProduct().getId().equals(PRODUCT_ID)
                        && allIds.contains(operation.getImageId())
                        && "CLEANUP_REQUIRED".equals(operation.getStatus())).count()).isZero();
        assertThat(operations.findAll().stream().filter(operation ->
                operation.getProduct().getId().equals(PRODUCT_ID)
                        && allIds.contains(operation.getImageId())
                        && "COMPLETE".equals(operation.getStatus())).count()).isGreaterThanOrEqualTo(12);
        System.out.println("STAGING_TANDA_5_FINAL_GALLERY=0 CLEANUP=COMPLETE");
    }

    private JsonNode upload(Cookie access) throws Exception {
        return mapper.readTree(mvc.perform(multipart(PATH).file(file()).cookie(access).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void assertGallery(Cookie access, List<Long> expectedIds, int primaryIndex) throws Exception {
        JsonNode result = gallery(access);
        assertThat(result.size()).isEqualTo(expectedIds.size());
        for (int index = 0; index < expectedIds.size(); index++) {
            assertThat(result.get(index).get("id").asLong()).isEqualTo(expectedIds.get(index));
            assertThat(result.get(index).get("position").asInt()).isEqualTo(index + 1);
            assertThat(result.get(index).get("primary").asBoolean()).isEqualTo(index == primaryIndex);
        }
    }

    private void assertPublicPrimary(long imageId, Cookie access) throws Exception {
        JsonNode detail = mapper.readTree(mvc.perform(get("/api/public/products/" + PRODUCT_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode adminGallery = gallery(access);
        assertThat(detail.get("images")).isEqualTo(adminGallery);
        String expected = null;
        for (JsonNode image : adminGallery) {
            if (image.get("id").asLong() == imageId) expected = image.get("thumbnailUrl").asText();
        }
        assertThat(expected).isNotNull();
        JsonNode page = mapper.readTree(mvc.perform(get("/api/public/products").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode summary : page.get("content")) {
            if (summary.get("id").asLong() == PRODUCT_ID) {
                assertThat(summary.get("primaryThumbnailUrl").asText()).isEqualTo(expected);
                found = true;
            }
        }
        assertThat(found).isTrue();
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
        ImageIO.write(new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile("file", "tanda-5.png", "image/png", out.toByteArray());
    }

    private void assertCloudFrontWebp(String url, int expectedWidth) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("image/webp");
            assertThat(response.body()).startsWith("RIFF".getBytes());
            assertThat(ImmutableImage.loader().fromBytes(response.body()).width).isEqualTo(expectedWidth);
            String key = URI.create(url).getPath().substring(1);
            URI directS3 = URI.create("https://" + properties.getImages().getBucket()
                    + ".s3.us-east-1.amazonaws.com/" + key);
            var denied = client.send(HttpRequest.newBuilder(directS3)
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(denied.statusCode()).isEqualTo(403);
        }
    }
}
