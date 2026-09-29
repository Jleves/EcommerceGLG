package com.ashenox.starter.catalog.category;

import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CategoryServiceMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private CategoryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private CategoryRepository repository;

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private AuthSessionService sessions;
    @Autowired private JWTUtil jwt;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void simultaneousCreatesUseUniqueIndexAfterBothExistenceChecksPass() throws Exception {
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(invocation -> {
            boolean exists = jdbcTemplate.queryForObject(
                    "select count(*) from categories where nombre_normalizado = ?",
                    Integer.class, invocation.getArgument(0, String.class)) > 0;
            checked.countDown();
            assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
            return exists;
        }).when(repository).existsByNombreNormalizado(eq("cemento"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(" Cemento "));
            var second = executor.submit(() -> attempt("CEMENTO"));
            Throwable firstFailure = first.get(20, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(20, TimeUnit.SECONDS);

            assertThat(checked.getCount()).as("fallos: %s / %s", firstFailure, secondFailure).isZero();
            assertThat(firstFailure == null ^ secondFailure == null).isTrue();
            assertThat(firstFailure != null ? firstFailure : secondFailure)
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(repository.count()).isEqualTo(1);
            assertThat(service.list()).hasSize(1);
        }
    }

    private Throwable attempt(String nombre) {
        try {
            service.create(new CreateCategoryRequest(nombre, null, "icon"));
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @Test
    void simultaneousHttpCreatesReturnCreatedAndConflictWithOneRow() throws Exception {
        User caller = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-hash").role(Role.USER).enabled(true).build());
        String sessionId = sessions.createSession(caller).session().getId();
        Cookie access = new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(caller.getId(), sessionId));
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(invocation -> {
            boolean exists = jdbcTemplate.queryForObject(
                    "select count(*) from categories where nombre_normalizado = ?",
                    Integer.class, invocation.getArgument(0, String.class)) > 0;
            checked.countDown();
            assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
            return exists;
        }).when(repository).existsByNombreNormalizado(eq("cemento"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> httpAttempt(" Cemento ", access));
            var second = executor.submit(() -> httpAttempt("CEMENTO", access));
            var responses = java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(checked.getCount()).isZero();
            assertThat(responses).containsExactlyInAnyOrder(201, 409);
            assertThat(repository.count()).isEqualTo(1);
        }
    }

    private int httpAttempt(String nombre, Cookie access) {
        try {
            var result = mvc.perform(post("/api/catalog/categories").cookie(access).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"" + nombre + "\",\"icono\":\"brick\"}"))
                    .andReturn();
            int statusCode = result.getResponse().getStatus();
            if (statusCode == 409) {
                assertThat(result.getResponse().getContentAsString())
                        .contains("\"status\":409", "\"code\":\"RESOURCE_CONFLICT\"", "\"requestId\":");
            }
            return statusCode;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
