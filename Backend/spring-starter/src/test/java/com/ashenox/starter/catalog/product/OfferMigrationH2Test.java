package com.ashenox.starter.catalog.product;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferMigrationH2Test {
    @Test
    void v13CreatesPositiveUniqueOfferWithoutChangingVariantPrice() throws Exception {
        String url = "jdbc:h2:mem:offers_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO categories (nombre, nombre_normalizado, icono, created_at, updated_at)
                    VALUES ('Materiales', 'materiales', 'brick', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO products (category_id, nombre, activo, destacado, created_at, updated_at)
                    VALUES (1, 'Arena', TRUE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO product_variants (product_id, precio_referencia, disponible, activo, created_at, updated_at)
                    VALUES (1, 100.00, TRUE, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO variant_offers (variant_id, precio_oferta, created_at, updated_at)
                    VALUES (1, 75.00, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            try (var rows = statement.executeQuery("""
                    SELECT v.precio_referencia, o.precio_oferta, o.created_at, o.updated_at
                    FROM product_variants v JOIN variant_offers o ON o.variant_id = v.id
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("precio_referencia")).isEqualByComparingTo("100.00");
                assertThat(rows.getBigDecimal("precio_oferta")).isEqualByComparingTo("75.00");
                assertThat(rows.getTimestamp("created_at")).isNotNull();
                assertThat(rows.getTimestamp("updated_at")).isNotNull();
                assertThat(rows.next()).isFalse();
            }
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO variant_offers (variant_id, precio_oferta, created_at, updated_at)
                    VALUES (1, 70.00, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE variant_offers SET precio_oferta = 0 WHERE variant_id = 1
                    """)).isInstanceOf(SQLException.class);
        }
    }
}
