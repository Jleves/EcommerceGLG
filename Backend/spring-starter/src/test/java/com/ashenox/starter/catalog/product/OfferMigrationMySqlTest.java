package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class OfferMigrationMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void v13EnforcesUniquePositivePriceAndLeavesBasePriceIntact() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (nombre, nombre_normalizado, icono, created_at, updated_at)
                    VALUES ('Materiales', 'materiales', 'brick', NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO products (category_id, nombre, activo, destacado, created_at, updated_at)
                    VALUES (1, 'Arena', TRUE, FALSE, NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO product_variants (product_id, precio_referencia, disponible, activo, created_at, updated_at)
                    VALUES (1, 100.00, TRUE, TRUE, NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO variant_offers (variant_id, precio_oferta, created_at, updated_at)
                    VALUES (1, 75.00, NOW(6), NOW(6))
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO variant_offers (variant_id, precio_oferta, created_at, updated_at)
                    VALUES (1, 70.00, NOW(6), NOW(6))
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE variant_offers SET precio_oferta = 0 WHERE variant_id = 1
                    """)).isInstanceOf(SQLException.class);
            try (var rows = statement.executeQuery("""
                    SELECT v.precio_referencia, o.precio_oferta
                    FROM product_variants v JOIN variant_offers o ON o.variant_id = v.id
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("precio_referencia")).isEqualByComparingTo("100.00");
                assertThat(rows.getBigDecimal("precio_oferta")).isEqualByComparingTo("75.00");
            }
        }
    }
}
