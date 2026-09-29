package com.ashenox.starter.user.service.impl;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.AdminUserService;
import com.ashenox.starter.user.service.AdminActor;
import com.ashenox.starter.user.dto.UserPageResponse;
import com.ashenox.starter.user.audit.AdminUserAuditEvent;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Instant;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import com.ashenox.starter.user.support.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final com.ashenox.starter.user.service.AdminMutationGuard mutationGuard;
    private final com.ashenox.starter.auth.challenge.service.ChallengeService challenges;
    private final com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository resetTokens;
    private final com.ashenox.starter.auth.session.service.AuthSessionService sessions;
    private final jakarta.validation.Validator validator;

    @Override
    @Transactional
    public UserResponse reactivate(Long id, AdminActor actor) {
        requireSuperAdmin(actor);
        User target = mutationGuard.lock(actor, id).target();
        if (target.isEnabled()) {
            auditReactivate(actor, id, AdminUserAuditEvent.Result.NO_OP);
            return UserResponse.from(target);
        }
        target.setEnabled(true);
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(target));
        auditReactivate(actor, id, AdminUserAuditEvent.Result.SUCCESS);
        return response;
    }

    private void auditReactivate(AdminActor actor, Long targetId, AdminUserAuditEvent.Result result) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String requestId = attributes instanceof ServletRequestAttributes servlet
                ? RequestLoggingFilter.getRequestId(servlet.getRequest()) : "unknown";
        events.publishEvent(new AdminUserAuditEvent(actor.id(), targetId,
                AdminUserAuditEvent.Operation.REACTIVATE, result, Instant.now(), requestId));
    }

    @Override
    @Transactional
    public UserResponse deactivate(Long id, AdminActor actor) {
        requireSuperAdmin(actor);
        var locked = mutationGuard.lock(actor, id);
        User target = locked.target();
        if (locked.isSelfTarget()) {
            throw new com.ashenox.starter.user.service.AdminUserConflictException(
                    com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_SELF_DEACTIVATION,
                    "No se puede desactivar la propia cuenta.");
        }
        if (locked.isLastActiveSuperAdmin()) {
            throw new com.ashenox.starter.user.service.AdminUserConflictException(
                    com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_LAST_SUPER_ADMIN,
                    "No se puede desactivar el último SUPER_ADMIN activo.");
        }
        if (!target.isEnabled()) {
            auditDeactivate(actor, id, AdminUserAuditEvent.Result.NO_OP);
            return UserResponse.from(target);
        }
        target.setEnabled(false);
        target.setSecurityVersion(target.getSecurityVersion() + 1);
        userRepository.saveAndFlush(target);
        challenges.invalidateAll(id);
        resetTokens.lockByUserId(id).filter(token -> token.getUsedAt() == null)
                .ifPresent(token -> token.setUsedAt(Instant.now()));
        sessions.revokeAllForUser(id);
        auditDeactivate(actor, id, AdminUserAuditEvent.Result.SUCCESS);
        return UserResponse.from(target);
    }

    private void auditDeactivate(AdminActor actor, Long targetId, AdminUserAuditEvent.Result result) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String requestId = attributes instanceof ServletRequestAttributes servlet
                ? RequestLoggingFilter.getRequestId(servlet.getRequest()) : "unknown";
        events.publishEvent(new AdminUserAuditEvent(actor.id(), targetId,
                AdminUserAuditEvent.Operation.DEACTIVATE, result, Instant.now(), requestId));
    }

    @Override
    @Transactional
    public UserResponse updateEmail(Long id, com.ashenox.starter.user.dto.UpdateUserEmailRequest request, AdminActor actor) {
        requireSuperAdmin(actor);
        User target = mutationGuard.lock(actor, id).target();
        if (request == null) throw new BusinessException("El email es obligatorio.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new jakarta.validation.ConstraintViolationException(violations);
        String email = EmailNormalizer.normalize(request.email());
        if (target.getEmail().equals(email)) {
            auditEmail(actor, id, AdminUserAuditEvent.Result.NO_OP);
            return UserResponse.from(target);
        }
        if (target.isEmailMfaEnabled()) {
            throw new com.ashenox.starter.user.service.AdminUserConflictException(
                    com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_EMAIL_MFA_ENABLED,
                    "El titular debe desactivar MFA antes de cambiar su correo.");
        }
        if (userRepository.existsByEmail(email)) throw emailInUse();
        target.setEmail(email);
        target.setSecurityVersion(target.getSecurityVersion() + 1);
        try {
            // invalidateAll refreshes the user. Flush first so it sees our own write even under
            // REPEATABLE READ; all subsequent invalidations still commit or roll back together.
            userRepository.saveAndFlush(target);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                    String constraint = violation.getConstraintName();
                    if (constraint != null && (constraint.equalsIgnoreCase("uk_users_email")
                            || constraint.equalsIgnoreCase("users.uk_users_email"))) throw emailInUse();
                }
            }
            throw exception;
        }
        challenges.invalidateAll(id);
        resetTokens.lockByUserId(id).filter(token -> token.getUsedAt() == null)
                .ifPresent(token -> token.setUsedAt(Instant.now()));
        sessions.revokeAllForUser(id); // flush/clear: never mutate target after this call
        auditEmail(actor, id, AdminUserAuditEvent.Result.SUCCESS);
        return UserResponse.from(target);
    }

    private com.ashenox.starter.user.service.AdminUserConflictException emailInUse() {
        return new com.ashenox.starter.user.service.AdminUserConflictException(
                com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_EMAIL_IN_USE,
                "El correo ya está en uso.");
    }

    private void auditEmail(AdminActor actor, Long targetId, AdminUserAuditEvent.Result result) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String requestId = attributes instanceof ServletRequestAttributes servlet
                ? RequestLoggingFilter.getRequestId(servlet.getRequest()) : "unknown";
        events.publishEvent(new AdminUserAuditEvent(actor.id(), targetId,
                AdminUserAuditEvent.Operation.UPDATE_EMAIL, result, Instant.now(), requestId));
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request, AdminActor actor) {
        requireSuperAdmin(actor);

        mutationGuard.lock(actor, null);

        String email = EmailNormalizer.normalize(request.email());
        com.ashenox.starter.auth.passwordchange.PasswordUpdateService.validate(request.password());
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .build();
        UserResponse response = UserResponse.from(userRepository.saveAndFlush(user));
        var attributes = RequestContextHolder.getRequestAttributes();
        String requestId = attributes instanceof ServletRequestAttributes servlet
                ? RequestLoggingFilter.getRequestId(servlet.getRequest()) : "unknown";
        events.publishEvent(new AdminUserAuditEvent(actor.id(), response.id(),
                AdminUserAuditEvent.Operation.CREATE, AdminUserAuditEvent.Result.SUCCESS,
                Instant.now(), requestId));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPageResponse list(int page, int size, AdminActor actor) {
        requireSuperAdmin(actor);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("page debe ser mayor o igual a 0 y size debe estar entre 1 y 100.");
        }
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        // JPA accepts only int offsets. Out-of-range pages still have an empty response under D06.
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = userRepository.count();
            if (pageable.getOffset() >= total) {
                return new UserPageResponse(List.of(), page, size, total, (int) Math.ceil((double) total / size));
            }
        }
        return UserPageResponse.from(userRepository.findAll(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id, AdminActor actor) {
        requireSuperAdmin(actor);
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario solicitado no existe.")));
    }

    private void requireSuperAdmin(AdminActor actor) {
        if (actor == null || actor.role() != Role.SUPER_ADMIN || actor.id() == null || actor.id() <= 0
                || actor.sessionId() == null || actor.sessionId().isBlank()) {
            throw new AccessDeniedException("Solamente SUPER_ADMIN puede gestionar usuarios");
        }
    }
}
