package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ProductVariantV11MigrationMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void upgradeKeepsBackfilledVariantValuesAndDropsProductColumns() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("9")).load().migrate();
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (nombre, nombre_normalizado, icono, created_at, updated_at)
                    VALUES ('Materiales', 'materiales', 'icon', NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO products (category_id, nombre, precio_referencia, disponible, created_at, updated_at)
                    VALUES (1, 'Arena', 12.50, FALSE, NOW(6), NOW(6))
                    """);
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT v.precio_referencia, v.disponible, v.activo
                     FROM products p JOIN product_variants v ON v.product_id = p.id
                     WHERE p.nombre = 'Arena'
                     """)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBigDecimal("precio_referencia")).isEqualByComparingTo("12.50");
            assertThat(rows.getBoolean("disponible")).isFalse();
            assertThat(rows.getBoolean("activo")).isTrue();
            assertThat(rows.next()).isFalse();
        }
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT precio_referencia FROM products"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeQuery("SELECT disponible FROM products"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
