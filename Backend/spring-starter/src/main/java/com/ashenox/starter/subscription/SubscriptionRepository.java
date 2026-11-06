package com.ashenox.starter.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

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

    private String contactColumn(SubscriptionRequest.Channel channel) {
        return channel == SubscriptionRequest.Channel.EMAIL ? "email" : "whatsapp";
    }

    private String consentColumn(SubscriptionRequest.Channel channel) {
        return channel == SubscriptionRequest.Channel.EMAIL ? "consentimiento_email_en" : "consentimiento_whatsapp_en";
    }

    private String withdrawalColumn(SubscriptionRequest.Channel channel) {
        return channel == SubscriptionRequest.Channel.EMAIL ? "baja_email_en" : "baja_whatsapp_en";
    }

    public long countByChannel(SubscriptionRequest.Channel channel) {
        String contact = contactColumn(channel);
        String consent = consentColumn(channel);
        return jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE " + contact + " IS NOT NULL AND " + consent + " IS NOT NULL", Long.class);
    }

    public List<AdminSubscriptionRow> findPageByChannel(SubscriptionRequest.Channel channel, int page, int size) {
        String contact = contactColumn(channel);
        String consent = consentColumn(channel);
        String withdrawal = withdrawalColumn(channel);
        return jdbc.query("SELECT id, " + contact + " AS contact, " + consent + " AS consented_at, " + withdrawal +
                        " AS withdrawn_at FROM subscriptions WHERE " + contact + " IS NOT NULL AND " + consent +
                        " IS NOT NULL ORDER BY " + consent + " DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> AdminSubscriptionRow.from(rs), size, (long) page * size);
    }

    public void streamActiveByChannel(SubscriptionRequest.Channel channel, Consumer<AdminSubscriptionRow> consumer) {
        String contact = contactColumn(channel);
        String consent = consentColumn(channel);
        String withdrawal = withdrawalColumn(channel);
        String base = "SELECT id, " + contact + " AS contact, " + consent + " AS consented_at, " + withdrawal +
                " AS withdrawn_at FROM subscriptions WHERE " + contact + " IS NOT NULL AND " + consent +
                " IS NOT NULL AND " + withdrawal + " IS NULL";
        AdminSubscriptionRow last = null;
        List<AdminSubscriptionRow> batch;
        do {
            if (last == null) {
                batch = jdbc.query(base + " ORDER BY " + consent + " DESC, id DESC LIMIT 500",
                        (rs, rowNum) -> AdminSubscriptionRow.from(rs));
            } else {
                LocalDateTime cursor = LocalDateTime.ofInstant(last.consentedAt(), ZoneOffset.UTC);
                batch = jdbc.query(base + " AND (" + consent + " < ? OR (" + consent + " = ? AND id < ?))" +
                                " ORDER BY " + consent + " DESC, id DESC LIMIT 500",
                        (rs, rowNum) -> AdminSubscriptionRow.from(rs), cursor, cursor, last.id());
            }
            batch.forEach(consumer);
            if (!batch.isEmpty()) last = batch.get(batch.size() - 1);
        } while (batch.size() == 500);
    }
}
