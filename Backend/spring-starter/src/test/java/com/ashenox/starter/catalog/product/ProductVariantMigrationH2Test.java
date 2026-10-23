package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVariantMigrationH2Test {
    @Test
    void v10BackfillsEveryProductWithoutChangingPriceOrAvailability() throws Exception {
        String url = "jdbc:h2:mem:variant_upgrade_" + UUID.randomUUID().toString().replace("-", "")
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
                    INSERT INTO products (category_id, nombre, precio_referencia, disponible, activo, created_at, updated_at)
                    VALUES (1, 'A', 1234567890123.45, TRUE, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
                           (1, 'B', 10.20, FALSE, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
                           (1, 'C', 0.01, TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
                           (1, 'D', 4.00, FALSE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT p.nombre, p.precio_referencia AS product_price,
                            v.precio_referencia AS variant_price, p.disponible AS product_available,
                            v.disponible AS variant_available, v.activo, v.sku, v.id
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
                assertThat(rows.getLong("id")).isPositive();
            }
            assertThat(count).isEqualTo(4);
        }
    }
}
