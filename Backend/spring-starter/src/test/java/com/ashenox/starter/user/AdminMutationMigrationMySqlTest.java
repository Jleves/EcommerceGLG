package com.ashenox.starter.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdminMutationMigrationMySqlTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test void upgradesV3AndPreservesUserStateWithExactlyOneCoordinationRow() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("3").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users (email, password_hash, role, enabled, created_at, updated_at,
                        password_change_failures, email_mfa_enabled, security_version)
                    values ('legacy@example.com', 'hash', 'SUPER_ADMIN', true, now(6), now(6), 2, true, 7)
                    """);
            var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (var rows = statement.executeQuery("select id from admin_mutation_lock")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("select email_mfa_enabled, security_version, password_change_failures from users")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
                assertThat(rows.getLong(2)).isEqualTo(7);
                assertThat(rows.getInt(3)).isEqualTo(2);
            }
            assertThatThrownBy(() -> statement.executeUpdate("insert into admin_mutation_lock values (2)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
