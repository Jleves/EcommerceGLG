package com.ashenox.starter.user.audit;

import java.time.Instant;

/** Immutable allowlist: never carry requests, users, emails or credentials. */
public record AdminUserAuditEvent(Long actorId, Long targetId, Operation operation,
                                  Result result, Instant timestamp, String requestId) {
    public enum Operation { CREATE, LIST, DETAIL, UPDATE_EMAIL, DEACTIVATE, REACTIVATE, UNKNOWN }
    public enum Result { SUCCESS, NO_OP, REJECTED, ERROR }
}
