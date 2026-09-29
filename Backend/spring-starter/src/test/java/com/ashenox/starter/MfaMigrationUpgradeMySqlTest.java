package com.ashenox.starter;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MfaMigrationUpgradeMySqlTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void migratesExistingUsersWithMfaDisabledAndVersionZero() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users (email, password_hash, role, enabled, created_at, updated_at, password_change_failures)
                    values ('legacy@example.com', 'hash', 'USER', true, now(6), now(6), 0)
                    """);
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     select email_mfa_enabled, email_mfa_enabled_at, security_version
                     from users where email = 'legacy@example.com'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean("email_mfa_enabled")).isFalse();
            assertThat(result.getTimestamp("email_mfa_enabled_at")).isNull();
            assertThat(result.getLong("security_version")).isZero();
        }
    }
}
