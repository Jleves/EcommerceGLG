package com.ashenox.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlSchemaIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void flywaySchemaIsValidAgainstMySql() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_challenges", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_rate_limits", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where version = '3'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = database() and table_name = 'auth_challenges'
                  and constraint_type = 'FOREIGN KEY'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'auth_challenges'
                  and index_name = 'idx_auth_challenges_user_purpose'
                """, Integer.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'auth_rate_limits'
                  and index_name = 'uk_auth_rate_limits_scope_subject'
                """, Integer.class)).isPositive();
    }

    @Test
    void categoriesHaveUniqueNormalizedNameInMySql() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '5'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'categories'
                  and index_name = 'uk_categories_nombre_normalizado' and non_unique = 0
                """, Integer.class)).isEqualTo(1);

        jdbcTemplate.update("""
                insert into categories
                    (nombre, nombre_normalizado, icono, activo, created_at, updated_at)
                values ('Cemento', 'cemento', 'cement', true, now(6), now(6))
                """);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into categories
                    (nombre, nombre_normalizado, icono, activo, created_at, updated_at)
                values (' CEMENTO ', 'cemento', 'cement', false, now(6), now(6))
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from categories where nombre_normalizado = 'cemento'", Integer.class))
                .isEqualTo(1);

        jdbcTemplate.update("""
                insert into categories
                    (nombre, nombre_normalizado, icono, activo, created_at, updated_at)
                values ('Arena', 'arena', 'sand', false, now(6), now(6))
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                update categories set nombre = 'CEMENTO', nombre_normalizado = 'cemento'
                where nombre_normalizado = 'arena'
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select nombre_normalizado from categories where nombre = 'Arena'", String.class))
                .isEqualTo("arena");
    }
}
