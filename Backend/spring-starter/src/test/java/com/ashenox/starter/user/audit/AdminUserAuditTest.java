package com.ashenox.starter.user.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;

class AdminUserAuditTest {
    @Test
    void errorScopeDeduplicationAndUntrustedFieldsAreSafeEvenWhenLoggingFails() {
        Logger logger = (Logger) LoggerFactory.getLogger("OPERATIONS");
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var responder = new com.ashenox.starter.shared.error.ApiErrorResponder(new tools.jackson.databind.ObjectMapper());
        try {
            var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/users/42");
            request.setAttribute(com.ashenox.starter.log.filter.RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, "escaped\n\"");
            responder.create(request, 500, com.ashenox.starter.shared.error.ApiErrorCode.INTERNAL_ERROR, "secret-exception-sentinel");
            responder.create(request, 500, com.ashenox.starter.shared.error.ApiErrorCode.INTERNAL_ERROR, "secret-exception-sentinel");
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("\"targetId\":42", "DETAIL", "ERROR", "escaped\\n\\\"")
                    .doesNotContain("secret-exception-sentinel", "\n");
            AdminUserAuditListener.rejected(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/users-secret"), 403);
            assertThat(appender.list).hasSize(1);
            AdminUserAuditListener.rejected(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/users/secret-path-sentinel"), 400);
            assertThat(appender.list.getLast().getFormattedMessage()).contains("UNKNOWN", "\"targetId\":null")
                    .doesNotContain("secret-path-sentinel");
            var broken = org.mockito.Mockito.mock(ch.qos.logback.core.Appender.class);
            org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                    .when(broken).doAppend(org.mockito.ArgumentMatchers.any());
            logger.addAppender(broken);
            try {
                var error = responder.create(new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/admin/users"),
                        403, com.ashenox.starter.shared.error.ApiErrorCode.ACCESS_DENIED, "denied");
                assertThat(error.status()).isEqualTo(403);
            } finally { logger.detachAppender(broken); }
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    void payloadIsAllowlistedEscapedAndIndependentAcrossThreads() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("OPERATIONS");
        var messages = new ConcurrentLinkedQueue<String>();
        var appender = new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) { messages.add(event.getFormattedMessage()); }
        };
        appender.start();
        logger.addAppender(appender);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (long i = 1; i <= 40; i++) {
                long id = i;
                futures.add(pool.submit(() -> {
                    org.slf4j.MDC.put("requestId", "wrong-thread-context");
                    try {
                        AdminUserAuditListener.emit(new AdminUserAuditEvent(id, id + 100,
                                AdminUserAuditEvent.Operation.CREATE, AdminUserAuditEvent.Result.SUCCESS,
                                Instant.EPOCH, "request-" + id + "\r\n\"\\"));
                    } finally { org.slf4j.MDC.clear(); }
                }));
            }
            for (var future : futures) future.get();
        } finally { logger.detachAppender(appender); appender.stop(); }
        assertThat(messages).hasSize(40);
        var mapper = new tools.jackson.databind.ObjectMapper();
        for (String message : messages) {
            assertThat(message).doesNotContain("\r", "\n", "wrong-thread-context");
            var node = mapper.readTree(message.substring("admin_user_audit ".length()));
            assertThat(node.size()).isEqualTo(6);
            long id = node.get("actorId").asLong();
            assertThat(node.get("targetId").asLong()).isEqualTo(id + 100);
            assertThat(node.get("requestId").asText()).isEqualTo("request-" + id + "\r\n\"\\");
        }
    }
}
