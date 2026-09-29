package com.ashenox.starter.user.service;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.AdminMutationLockRepository;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Entry point before any administrative write or user/session lock in the caller's transaction.
 * Clears the persistence context after acquiring coordination: callers must have no pending writes
 * and must use the returned managed users instead of references loaded before this call.
 */
@Service
@RequiredArgsConstructor
public class AdminMutationGuard {
    private final AdminMutationLockRepository coordination;
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public LockedUsers lock(AdminActor actor, Long targetId) {
        if (actor == null || actor.id() == null || actor.id() <= 0 || actor.role() != Role.SUPER_ADMIN
                || actor.sessionId() == null || actor.sessionId().isBlank()) throw denied();
        // Global order: singleton -> users by ID (actor, target, active super-admins) -> actor session.
        // Auth flows lock one user before sessions/challenges and never acquire the singleton.
        coordination.acquire();
        // Start the write unit with a fresh persistence context. Locking queries otherwise reuse
        // stale managed users or fail on a previously loaded session version before revalidation.
        // Callers must invoke this guard before any pending writes (clear deliberately does not flush).
        entityManager.clear();
        var locked = users.lockAdministrativeUsers(actor.id(), targetId);
        var currentActor = locked.stream().filter(user -> user.getId().equals(actor.id()))
                .findFirst().orElseThrow(AdminMutationGuard::denied);
        if (!currentActor.isEnabled() || currentActor.getRole() != Role.SUPER_ADMIN) throw denied();
        var session = sessions.lockByIdAndUserId(actor.sessionId(), actor.id())
                .orElseThrow(AdminMutationGuard::denied);
        if (!session.getUser().getId().equals(actor.id()) || !session.isActiveAt(Instant.now())) throw denied();
        User target = targetId == null ? null : locked.stream().filter(user -> user.getId().equals(targetId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("El usuario solicitado no existe."));
        long activeSuperAdmins = locked.stream()
                .filter(user -> user.isEnabled() && user.getRole() == Role.SUPER_ADMIN).count();
        return new LockedUsers(currentActor, target, activeSuperAdmins);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Solamente SUPER_ADMIN con sesión vigente puede gestionar usuarios");
    }

    /** Valid only inside the transaction holding the locks; no mutation endpoint is introduced here. */
    public record LockedUsers(User actor, User target, long activeSuperAdmins) {
        public boolean isSelfTarget() {
            return target != null && actor.getId().equals(target.getId());
        }

        public boolean isLastActiveSuperAdmin() {
            return target != null && target.isEnabled() && target.getRole() == Role.SUPER_ADMIN
                    && activeSuperAdmins <= 1;
        }
    }
}
