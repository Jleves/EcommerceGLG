package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.dto.UpdateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CharacteristicRenameMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired CharacteristicService characteristics;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uniqueConstraintRejectsTwoNamesAfterBothPrechecksSawAvailable() throws Exception {
        String suffix = UUID.randomUUID().toString();
        long first = characteristics.create(new CreateCharacteristicRequest("Primera " + suffix)).id();
        long second = characteristics.create(new CreateCharacteristicRequest("Segunda " + suffix)).id();
        String target = "compartida " + suffix;

        try (Connection firstConnection = dataSource.getConnection();
             Connection secondConnection = dataSource.getConnection();
             var executor = Executors.newSingleThreadExecutor()) {
            firstConnection.setAutoCommit(false);
            secondConnection.setAutoCommit(false);
            try {
                assertThat(nameExists(firstConnection, target)).isFalse();
                assertThat(nameExists(secondConnection, target)).isFalse();
                updateName(firstConnection, first, target);

                CountDownLatch started = new CountDownLatch(1);
                var competingUpdate = executor.submit(() -> {
                    started.countDown();
                    try {
                        updateName(secondConnection, second, target);
                        secondConnection.commit();
                        return false;
                    } catch (SQLException expectedDuplicate) {
                        secondConnection.rollback();
                        return "23000".equals(expectedDuplicate.getSQLState());
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                firstConnection.commit();
                assertThat(competingUpdate.get(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                firstConnection.rollback();
                secondConnection.rollback();
            }
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM characteristics WHERE nombre_normalizado = ?",
                Integer.class, target)).isEqualTo(1);
        assertThat(characteristics.getById(first).nombre()).isEqualTo(target);
        assertThat(characteristics.getById(second).nombre()).isEqualTo("Segunda " + suffix);
        assertThatThrownBy(() -> characteristics.update(second, new UpdateCharacteristicRequest(target)))
                .isInstanceOf(com.ashenox.starter.catalog.characteristic.service.CharacteristicConflictException.class);
    }

    private boolean nameExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT count(*) FROM characteristics WHERE nombre_normalizado = ?")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) != 0;
            }
        }
    }

    private void updateName(Connection connection, long id, String name) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE characteristics SET nombre = ?, nombre_normalizado = ? WHERE id = ?")) {
            statement.setString(1, name);
            statement.setString(2, name);
            statement.setLong(3, id);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }
}
