package com.ashenox.starter.auth;

import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in real browser suite: mvnw -Dtest=MfaBrowserIT test. Requires npm ci and Chromium. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class MfaBrowserIT {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Container
    static final GenericContainer<?> MAIL = new GenericContainer<>("axllent/mailpit:v1.27.8")
            .withExposedPorts(1025, 8025);
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JavaMailSender mailSender;

    @Test void completesBrowserLifecycleForEveryRole() throws Exception {
        // Only this isolated test context disables TLS/auth for the disposable local SMTP sink.
        var smtp = (JavaMailSenderImpl) mailSender;
        smtp.setHost(MAIL.getHost());
        smtp.setPort(MAIL.getMappedPort(1025));
        smtp.getJavaMailProperties().put("mail.smtp.auth", "false");
        smtp.getJavaMailProperties().put("mail.smtp.starttls.enable", "false");
        smtp.getJavaMailProperties().put("mail.smtp.starttls.required", "false");
        for (Role role : Role.values()) {
            users.saveAndFlush(User.builder().email(role.name().toLowerCase(java.util.Locale.ROOT) + "@example.com")
                    .passwordHash(passwords.encode("browser-test-password")).role(role).enabled(true).build());
        }
        var frontend = Path.of("../../Frontend/react-starter").toAbsolutePath().normalize();
        var builder = new ProcessBuilder("node", "e2e/mfa-lifecycle.mjs").directory(frontend.toFile())
                .inheritIO();
        builder.environment().put("MFA_API_URL", "http://localhost:" + port);
        builder.environment().put("MFA_MAIL_URL", "http://" + MAIL.getHost() + ":" + MAIL.getMappedPort(8025));
        Process browser = builder.start();
        try {
            assertThat(browser.waitFor(180, TimeUnit.SECONDS)).as("browser suite finished").isTrue();
            assertThat(browser.exitValue()).as("browser assertions passed").isZero();
        } finally {
            if (browser.isAlive()) browser.destroyForcibly();
        }
        assertThat(users.findAll()).allSatisfy(user -> {
            assertThat(user.isEmailMfaEnabled()).isFalse();
            assertThat(user.getSecurityVersion()).isEqualTo(2);
        });
    }
}
