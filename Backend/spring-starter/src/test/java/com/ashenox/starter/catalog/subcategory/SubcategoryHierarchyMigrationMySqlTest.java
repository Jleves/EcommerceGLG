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
class SubcategoryHierarchyMigrationMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void upgradingFromV8PreservesSubcategoriesAndProductReferences() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("8")).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO categories (id, nombre, nombre_normalizado, icono, activo, created_at, updated_at) VALUES (1, 'Materiales', 'materiales', 'icon', TRUE, NOW(6), NOW(6))");
            statement.executeUpdate("INSERT INTO subcategories (id, category_id, nombre, nombre_normalizado, created_at, updated_at) VALUES (1, 1, 'Chapas', 'chapas', NOW(6), NOW(6)), (2, 1, 'Arena', 'arena', NOW(6), NOW(6))");
            statement.executeUpdate("INSERT INTO products (id, category_id, subcategory_id, nombre, precio_referencia, disponible, activo, destacado, created_at, updated_at) VALUES (1, 1, 1, 'Producto', 10.00, TRUE, TRUE, FALSE, NOW(6), NOW(6))");
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT s.id, s.parent_subcategory_id, p.subcategory_id FROM subcategories s LEFT JOIN products p ON p.subcategory_id = s.id ORDER BY s.id")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(1);
            assertThat(rows.getObject("parent_subcategory_id")).isNull();
            assertThat(rows.getLong("subcategory_id")).isEqualTo(1);
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(2);
            assertThat(rows.getObject("parent_subcategory_id")).isNull();
            assertThat(rows.getObject("subcategory_id")).isNull();
            assertThat(rows.next()).isFalse();
        }
    }
}
