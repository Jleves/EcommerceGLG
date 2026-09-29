package com.ashenox.starter.email.service;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class SmtpMfaEmailSender implements MfaEmailSender {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties properties;

    @Override
    public void sendCode(String recipient, ChallengePurpose purpose, String code, Instant expiresAt) {
        String template = switch (purpose) {
            case LOGIN -> "mfa-login";
            case ENABLE -> "mfa-enable";
            case DISABLE -> "mfa-disable";
        };
        String subject = switch (purpose) {
            case LOGIN -> "Código para iniciar sesión";
            case ENABLE -> "Código para activar la verificación en dos pasos";
            case DISABLE -> "Código para desactivar la verificación en dos pasos";
        };
        Context context = new Context();
        context.setVariable("code", code);
        context.setVariable("expiresAt", expiresAt.toString());
        send(recipient, subject, template, context);
    }

    @Override
    public void sendSettingsChanged(String recipient, boolean enabled) {
        Context context = new Context();
        context.setVariable("enabled", enabled);
        send(recipient, "Cambio en la verificación en dos pasos", "mfa-settings-changed", context);
    }

    private void send(String recipient, String subject, String template, Context context) {
        try {
            String html = templateEngine.process(template, context);
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.getMail().getUsername());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception exception) {
            // Do not propagate provider details or the mail body to error handlers/loggers.
            throw new IllegalStateException("No se pudo entregar el correo MFA");
        }
    }
}
