package com.ashenox.starter.subscription;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record AdminSubscriptionRow(long id, String contact, Instant consentedAt,
                                   Instant withdrawnAt, boolean active) {
    public static AdminSubscriptionRow from(ResultSet rs) throws SQLException {
        LocalDateTime consent = rs.getObject("consented_at", LocalDateTime.class);
        LocalDateTime withdrawal = rs.getObject("withdrawn_at", LocalDateTime.class);
        return new AdminSubscriptionRow(rs.getLong("id"), rs.getString("contact"),
                consent.toInstant(ZoneOffset.UTC), withdrawal == null ? null : withdrawal.toInstant(ZoneOffset.UTC), withdrawal == null);
    }
}
