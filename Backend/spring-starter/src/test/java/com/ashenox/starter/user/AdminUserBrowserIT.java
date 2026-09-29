package com.ashenox.starter.user;

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

/** Opt-in HTTP/browser suite with disposable MySQL and Mailpit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AdminUserBrowserIT {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Container static final GenericContainer<?> MAIL = new GenericContainer<>("axllent/mailpit:v1.27.8")
            .withExposedPorts(1025, 8025);
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JavaMailSender mailSender;

    @Test void administrativeLifecycleAcrossBrowserHttpDatabaseAndSmtp() throws Exception {
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
        var builder = new ProcessBuilder("node", "e2e/admin-user-lifecycle.mjs").directory(frontend.toFile())
                .inheritIO();
        builder.environment().put("ADMIN_API_URL", "http://localhost:" + port);
        builder.environment().put("ADMIN_MAIL_URL", "http://" + MAIL.getHost() + ":" + MAIL.getMappedPort(8025));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        Process browser = builder.start();
        try {
            assertThat(browser.waitFor(180, TimeUnit.SECONDS)).as("browser suite finished").isTrue();
            assertThat(browser.exitValue()).as("browser assertions passed").isZero();
        } finally {
            if (browser.isAlive()) browser.destroyForcibly();
            logger.detachAppender(appender);
            appender.stop();
        }
        var audit = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
        for (String operation : new String[]{"CREATE", "UPDATE_EMAIL", "DEACTIVATE", "REACTIVATE"}) {
            assertThat(audit).anySatisfy(line -> assertThat(line).contains("\"operation\":\"" + operation + "\"", "\"result\":\"SUCCESS\""));
        }
        assertThat(audit).anySatisfy(line -> assertThat(line).contains("\"operation\":\"UPDATE_EMAIL\"", "\"result\":\"REJECTED\""));
        assertThat(audit).allSatisfy(line -> assertThat(line).doesNotContain("browser-test-password"));
        var target = users.findByEmail("managed@example.com").orElseThrow();
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.isEmailMfaEnabled()).isFalse();
        assertThat(target.getSecurityVersion()).isGreaterThanOrEqualTo(3);
    }
}
