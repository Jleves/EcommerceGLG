package com.ashenox.starter.subscription;

import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminSubscriptionHttpTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired JWTUtil jwt;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUsers = new ArrayList<>();
    private String contact;

    @AfterEach
    void clean() {
        if (contact != null) jdbc.update("DELETE FROM subscriptions WHERE email = ?", contact);
        for (Long id : createdUsers) {
            jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", id);
            users.deleteById(id);
        }
    }

    @Test
    void exportCompletesOverHttpWithJwtCookieAndPreservesRoleChecks() throws Exception {
        contact = "async-export-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO subscriptions (email, consentimiento_email_en) VALUES (?, ?)",
                contact, LocalDateTime.now(ZoneOffset.UTC));
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/admin/subscriptions/export?channel=EMAIL");

        try (HttpClient client = HttpClient.newHttpClient()) {
            for (Role role : List.of(Role.ADMIN, Role.SUPER_ADMIN)) {
                HttpResponse<byte[]> response = download(client, endpoint, accessCookie(role));
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("content-type").orElse("")).contains("spreadsheetml.sheet");
                try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
                    var sheet = workbook.getSheetAt(0);
                    assertThat(sheet.getLastRowNum()).isEqualTo(1);
                    assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(contact);
                }
            }
            assertThat(download(client, endpoint, accessCookie(Role.USER)).statusCode()).isEqualTo(403);
            assertThat(download(client, endpoint, null).statusCode()).isEqualTo(401);
        }
    }

    private HttpResponse<byte[]> download(HttpClient client, URI endpoint, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30));
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String accessCookie(Role role) {
        User user = users.saveAndFlush(User.builder()
                .email("async-" + role + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(role).enabled(true).build());
        createdUsers.add(user.getId());
        String sessionId = sessions.createSession(user).session().getId();
        return "ACCESS_TOKEN=" + jwt.generateToken(user.getId(), sessionId);
    }
}
