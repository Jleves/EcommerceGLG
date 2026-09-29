package com.ashenox.starter.user.audit;

import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.security.model.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;

@Component
public class AdminUserAuditListener {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RECORDED = AdminUserAuditListener.class.getName();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(AdminUserAuditEvent event) {
        emit(event);
    }

    public static void rejected(HttpServletRequest request, int status) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(path.equals("/api/admin/users") || path.startsWith("/api/admin/users/"))
                || status < 400 || request.getAttribute(RECORDED) != null) return;
        request.setAttribute(RECORDED, true);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actor = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user ? user.id() : null;
        var operation = AdminUserAuditEvent.Operation.UNKNOWN;
        Long target = null;
        if (path.equals("/api/admin/users")) {
            if (request.getMethod().equals("POST")) operation = AdminUserAuditEvent.Operation.CREATE;
            if (request.getMethod().equals("GET")) operation = AdminUserAuditEvent.Operation.LIST;
        } else if (request.getMethod().equals("GET")
                || (request.getMethod().equals("PATCH") && path.endsWith("/email"))
                || (request.getMethod().equals("POST") && (path.endsWith("/deactivate") || path.endsWith("/reactivate")))) {
            String id = path.substring("/api/admin/users/".length());
            boolean emailChange = request.getMethod().equals("PATCH");
            boolean deactivation = request.getMethod().equals("POST") && path.endsWith("/deactivate");
            boolean reactivation = request.getMethod().equals("POST") && path.endsWith("/reactivate");
            if (emailChange) id = id.endsWith("/email") ? id.substring(0, id.length() - "/email".length()) : "";
            if (deactivation) id = id.substring(0, id.length() - "/deactivate".length());
            if (reactivation) id = id.substring(0, id.length() - "/reactivate".length());
            if (id.matches("[1-9][0-9]{0,18}")) {
                try {
                    target = Long.valueOf(id);
                    operation = reactivation ? AdminUserAuditEvent.Operation.REACTIVATE
                            : deactivation ? AdminUserAuditEvent.Operation.DEACTIVATE
                            : emailChange ? AdminUserAuditEvent.Operation.UPDATE_EMAIL : AdminUserAuditEvent.Operation.DETAIL;
                } catch (NumberFormatException ignored) { /* Unknown target. */ }
            }
        }
        emit(new AdminUserAuditEvent(actor, target, operation,
                status >= 500 ? AdminUserAuditEvent.Result.ERROR : AdminUserAuditEvent.Result.REJECTED,
                Instant.now(), RequestLoggingFilter.getRequestId(request)));
    }

    static void emit(AdminUserAuditEvent event) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("actorId", event.actorId());
            payload.put("targetId", event.targetId());
            payload.put("operation", event.operation());
            payload.put("result", event.result());
            payload.put("timestamp", event.timestamp().toString());
            payload.put("requestId", event.requestId());
            LoggerFactory.getLogger("OPERATIONS").info("admin_user_audit {}", JSON.writeValueAsString(payload));
        } catch (RuntimeException ignored) {
            // D04: logging failure must never change the confirmed operation or HTTP error.
        }
    }
}
