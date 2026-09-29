package com.ashenox.starter.catalog.category;

import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.category.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
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
}
