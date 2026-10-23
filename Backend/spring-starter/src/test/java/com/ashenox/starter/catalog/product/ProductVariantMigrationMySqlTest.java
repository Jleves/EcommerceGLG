package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ProductVariantMigrationMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void v10BackfillsExistingProductsAndPreservesTheirValues() throws Exception {
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
                    INSERT INTO products (category_id, nombre, precio_referencia, disponible, activo, created_at, updated_at)
                    VALUES (1, 'A', 1234567890123.45, TRUE, TRUE, NOW(6), NOW(6)),
                           (1, 'B', 10.20, FALSE, TRUE, NOW(6), NOW(6)),
                           (1, 'C', 0.01, TRUE, FALSE, NOW(6), NOW(6)),
                           (1, 'D', 4.00, FALSE, FALSE, NOW(6), NOW(6))
                    """);
        }

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("10")).load().migrate();

        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT p.precio_referencia AS product_price, v.precio_referencia AS variant_price,
                            p.disponible AS product_available, v.disponible AS variant_available,
                            v.activo, v.sku
                     FROM products p JOIN product_variants v ON v.product_id = p.id
                     ORDER BY p.id
                     """)) {
            int count = 0;
            while (rows.next()) {
                count++;
                assertThat(rows.getBigDecimal("variant_price"))
                        .isEqualByComparingTo(rows.getBigDecimal("product_price"));
                assertThat(rows.getBoolean("variant_available"))
                        .isEqualTo(rows.getBoolean("product_available"));
                assertThat(rows.getBoolean("activo")).isTrue();
                assertThat(rows.getString("sku")).isNull();
            }
            assertThat(count).isEqualTo(4);
        }
    }
}
