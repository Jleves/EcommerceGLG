package com.ashenox.starter.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class SubscriptionRepository {
    private final JdbcTemplate jdbc;

    public void subscribe(SubscriptionRequest.Channel channel, String contact, Instant now) {
        String column = channel == SubscriptionRequest.Channel.EMAIL ? "email" : "whatsapp";
        String consentColumn = channel == SubscriptionRequest.Channel.EMAIL
                ? "consentimiento_email_en" : "consentimiento_whatsapp_en";
        String withdrawnColumn = channel == SubscriptionRequest.Channel.EMAIL
                ? "baja_email_en" : "baja_whatsapp_en";
        LocalDateTime acceptedAtUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("INSERT IGNORE INTO subscriptions (" + column + ", " + consentColumn + ") VALUES (?, ?)",
                contact, acceptedAtUtc);
        // The unique contact key makes concurrent submissions converge on the same row.
        // A repeated active submission keeps its original consent time; a withdrawn channel gets a new one.
        jdbc.update("UPDATE subscriptions SET " + consentColumn + " = CASE WHEN " + withdrawnColumn +
                        " IS NOT NULL THEN ? ELSE " + consentColumn + " END, " + withdrawnColumn +
                        " = NULL WHERE " + column + " = ?",
                acceptedAtUtc, contact);
    }
}
