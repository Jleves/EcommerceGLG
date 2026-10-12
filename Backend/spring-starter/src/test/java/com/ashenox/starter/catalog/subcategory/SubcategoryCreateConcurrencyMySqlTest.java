package com.ashenox.starter.catalog.subcategory;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.catalog.subcategory.service.SubcategoryService;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SubcategoryCreateConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CategoryRepository categories;
    @Autowired SubcategoryService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SubcategoryRepository subcategories;

    @Test
    void concurrentCreatesOfSameNormalizedNameLeaveOneRow() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Category parent = categories.saveAndFlush(Category.builder().nombre("Parent " + suffix)
                .nombreNormalizado("parent-" + suffix).icono("icon").build());
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(invocation -> {
            boolean exists = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM subcategories WHERE category_id = ? AND nombre_normalizado = ?
                    """, Integer.class, parent.getId(), "chapas") > 0;
            checked.countDown();
            assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
            return exists;
        }).when(subcategories).existsByCategoryIdAndNombreNormalizado(eq(parent.getId()), eq("chapas"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attempt(parent.getId(), " Chapas "));
            var second = executor.submit(() -> attempt(parent.getId(), "CHAPAS"));
            Throwable firstFailure = first.get(20, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(20, TimeUnit.SECONDS);
            assertThat(checked.getCount()).isZero();
            assertThat(firstFailure == null ^ secondFailure == null).isTrue();
            assertThat(firstFailure == null ? secondFailure : firstFailure)
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM subcategories WHERE category_id = ? AND nombre_normalizado = 'chapas'
                    """, Integer.class, parent.getId())).isEqualTo(1);
        }
    }

    private Throwable attempt(Long parentId, String name) {
        try {
            service.create(new CreateSubcategoryRequest(parentId, name, null));
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
