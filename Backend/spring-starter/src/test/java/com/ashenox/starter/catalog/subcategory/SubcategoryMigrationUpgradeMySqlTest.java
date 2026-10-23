package com.ashenox.starter.catalog.subcategory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SubcategoryMigrationUpgradeMySqlTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void upgradingFromV7PreservesProductAndDirectCategory() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("7"))
                .load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (nombre, nombre_normalizado, icono, activo, created_at, updated_at)
                    VALUES ('Materiales', 'materiales', 'icon', TRUE, NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO products (category_id, nombre, precio_referencia, disponible, activo, destacado, created_at, updated_at)
                    VALUES (1, 'Arena', 10.00, TRUE, TRUE, FALSE, NOW(6), NOW(6))
                    """);
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT p.nombre, p.category_id, p.subcategory_id,
                            v.precio_referencia, c.nombre AS category_name
                     FROM products p JOIN categories c ON c.id = p.category_id
                     JOIN product_variants v ON v.product_id = p.id
                     WHERE p.nombre = 'Arena'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("nombre")).isEqualTo("Arena");
            assertThat(result.getString("category_name")).isEqualTo("Materiales");
            assertThat(result.getLong("category_id")).isEqualTo(1L);
            assertThat(result.getObject("subcategory_id")).isNull();
            assertThat(result.getBigDecimal("precio_referencia")).isEqualByComparingTo("10.00");
            assertThat(result.next()).isFalse();
        }
    }
}
