package com.ashenox.starter.email;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.email.config.MailConfig;
import com.ashenox.starter.email.service.SmtpMfaEmailSender;
import com.ashenox.starter.shared.config.AppProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class SmtpMfaEmailSenderTest {
    @ParameterizedTest
    @EnumSource(ChallengePurpose.class)
    void rendersAndDeliversEachCodeTemplateThroughSmtp(ChallengePurpose purpose) throws Exception {
        try (SmtpServer server = new SmtpServer(Mode.ACCEPT)) {
            adapter(server).sendCode("recipient@example.com", purpose, "004271", Instant.parse("2030-01-01T12:00:00Z"));
            MimeMessage message = server.message();
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
            assertThat(message.getFrom()[0].toString()).isEqualTo("sender@example.com");
            String action = switch (purpose) {
                case LOGIN -> "iniciar sesión";
                case ENABLE -> "activar la verificación";
                case DISABLE -> "desactivar la verificación";
            };
            assertThat(message.getSubject()).contains(action);
            assertThat(message.getContent().toString()).contains("004271", action, "2030-01-01T12:00:00Z")
                    .doesNotContain("${code}", "${expiresAt}");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deliversSettingsNotificationWithoutACode(boolean enabled) throws Exception {
        try (SmtpServer server = new SmtpServer(Mode.ACCEPT)) {
            adapter(server).sendSettingsChanged("recipient@example.com", enabled);
            String html = server.message().getContent().toString();
            assertThat(html).contains(enabled ? "fue activada" : "fue desactivada")
                    .doesNotContain(enabled ? "fue desactivada" : "fue activada", "000000");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"REJECT", "TIMEOUT"})
    void smtpRejectionAndReadTimeoutAreSanitized(Mode mode) throws Exception {
        try (SmtpServer server = new SmtpServer(mode)) {
            assertThatThrownBy(() -> adapter(server).sendCode("recipient@example.com",
                    ChallengePurpose.LOGIN, "004271", Instant.now().plusSeconds(300)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No se pudo entregar el correo MFA")
                    .hasNoCause();
        }
    }

    @Test
    void productionMailConfigurationHasBoundedTimeoutsAndRequiresTls() {
        var sender = (JavaMailSenderImpl) new MailConfig(properties()).javaMailSender();
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "10000")
                .containsEntry("mail.smtp.writetimeout", "10000")
                .containsEntry("mail.smtp.starttls.required", "true");
    }

    private SmtpMfaEmailSender adapter(SmtpServer server) {
        AppProperties properties = properties();
        var sender = (JavaMailSenderImpl) new MailConfig(properties).javaMailSender();
        sender.setPort(server.port());
        // Local test server only. Production continues to require authenticated STARTTLS.
        sender.getJavaMailProperties().put("mail.smtp.auth", "false");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "false");
        sender.getJavaMailProperties().put("mail.smtp.starttls.required", "false");
        sender.getJavaMailProperties().put("mail.smtp.timeout", server.mode == Mode.TIMEOUT ? "200" : "3000");
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
        return new SmtpMfaEmailSender(sender, templates, properties);
    }

    private AppProperties properties() {
        var properties = new AppProperties();
        properties.getMail().setHost("127.0.0.1");
        properties.getMail().setUsername("sender@example.com");
        properties.getMail().setPassword("test-password");
        return properties;
    }

    private enum Mode { ACCEPT, REJECT, TIMEOUT }

    /** Minimal local SMTP peer: tests real JavaMail IO without an external service or credentials. */
    private static final class SmtpServer implements AutoCloseable {
        private final Mode mode;
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<byte[]> received;
        private volatile Socket connection;

        SmtpServer(Mode mode) throws IOException {
            this.mode = mode;
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(5000);
            received = executor.submit(this::receive);
        }

        int port() { return server.getLocalPort(); }

        MimeMessage message() throws Exception {
            return new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(received.get(5, TimeUnit.SECONDS)));
        }

        private byte[] receive() throws IOException {
            try (Socket socket = server.accept()) {
                connection = socket;
                socket.setSoTimeout(5000);
                if (mode == Mode.TIMEOUT) {
                    // Withhold the greeting until JavaMail's read timeout closes the connection.
                    socket.getInputStream().read();
                    return new byte[0];
                }
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                reply(writer, "220 localhost test SMTP");
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                String line;
                boolean body = false;
                while ((line = reader.readLine()) != null) {
                    if (body) {
                        if (line.equals(".")) {
                            body = false;
                            reply(writer, "250 queued");
                        } else {
                            data.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
                        }
                    } else if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                        reply(writer, "250 localhost");
                    } else if (line.startsWith("RCPT") && mode == Mode.REJECT) {
                        reply(writer, "550 rejected sensitive-provider-detail");
                    } else if (line.equals("DATA")) {
                        body = true;
                        reply(writer, "354 end with dot");
                    } else if (line.equals("QUIT")) {
                        reply(writer, "221 bye");
                        break;
                    } else {
                        reply(writer, "250 ok");
                    }
                }
                return data.toByteArray();
            }
        }

        private void reply(BufferedWriter writer, String line) throws IOException {
            writer.write(line + "\r\n");
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            server.close();
            if (connection != null) connection.close();
            executor.shutdownNow();
        }
    }
}
