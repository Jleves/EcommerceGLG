package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductVariantV11MigrationH2Test {
    @Test
    void upgradeKeepsBackfilledVariantValuesAndDropsProductColumns() throws Exception {
        String url = "jdbc:h2:mem:v11_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "")
                .target(MigrationVersion.fromVersion("9")).load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (nombre, nombre_normalizado, icono, created_at, updated_at)
                    VALUES ('Materiales', 'materiales', 'icon', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO products (category_id, nombre, precio_referencia, disponible, created_at, updated_at)
                    VALUES (1, 'Arena', 12.50, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
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
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT precio_referencia FROM products"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeQuery("SELECT disponible FROM products"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
