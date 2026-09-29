package com.ashenox.starter.user;

import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.*;
import com.ashenox.starter.user.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;

class AdminMutationGuardTest {
    private final AdminMutationLockRepository coordination = mock(AdminMutationLockRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AdminMutationGuard guard = new AdminMutationGuard(coordination, users, sessions, em);
    private final AdminActor actor = new AdminActor(1L, Role.SUPER_ADMIN, "sid");
    private final User user = User.builder().id(1L).enabled(true).role(Role.SUPER_ADMIN).build();

    private AuthSession prepare(Long target) {
        when(users.lockAdministrativeUsers(1L, target)).thenReturn(List.of(user));
        var session = new AuthSession();
        session.setUser(user);
        session.setExpiresAt(Instant.now().plusSeconds(60));
        when(sessions.lockByIdAndUserId("sid", 1L)).thenReturn(Optional.of(session));
        return session;
    }

    @Test void validatesCurrentRoleAndEnabledStateAfterCoordination() {
        when(users.lockAdministrativeUsers(1L, null)).thenReturn(List.of(user));
        for (Role role : new Role[]{Role.ADMIN, Role.USER}) {
            user.setRole(role);
            assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        }
        user.setRole(Role.SUPER_ADMIN);
        user.setEnabled(false);
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessions);
    }

    @Test void rejectsMissingActorAndUntrustedContext() {
        assertThatThrownBy(() -> guard.lock(null, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(coordination, users, sessions, em);
        when(users.lockAdministrativeUsers(1L, null)).thenReturn(List.of());
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessions);
    }

    @Test void rejectsForeignMissingExpiredAndRevokedSessions() {
        var session = prepare(null);
        session.setUser(User.builder().id(2L).build());
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        session.setUser(user);
        session.setExpiresAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        session.setExpiresAt(Instant.now().plusSeconds(60));
        session.setRevokedAt(Instant.now());
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
        when(sessions.lockByIdAndUserId("sid", 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.lock(actor, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void selfTargetIsLockedOnceAndRulesUseLockedState() {
        var session = prepare(1L);
        var result = guard.lock(actor, 1L);
        assertThat(result.actor()).isSameAs(result.target());
        assertThat(result.isSelfTarget()).isTrue();
        assertThat(result.isLastActiveSuperAdmin()).isTrue();
        var order = inOrder(coordination, users, em, sessions);
        order.verify(coordination).acquire();
        order.verify(em).clear();
        order.verify(users).lockAdministrativeUsers(1L, 1L);
        order.verify(sessions).lockByIdAndUserId("sid", 1L);
        verify(users, times(1)).lockAdministrativeUsers(1L, 1L);
        var target = User.builder().id(2L).role(Role.SUPER_ADMIN).enabled(true).build();
        assertThat(new AdminMutationGuard.LockedUsers(user, target, 2).isLastActiveSuperAdmin()).isFalse();
        assertThat(new AdminMutationGuard.LockedUsers(user, target, 2).isSelfTarget()).isFalse();
        target.setEnabled(false);
        assertThat(new AdminMutationGuard.LockedUsers(user, target, 1).isLastActiveSuperAdmin()).isFalse();
        target.setEnabled(true);
        target.setRole(Role.USER);
        assertThat(new AdminMutationGuard.LockedUsers(user, target, 1).isLastActiveSuperAdmin()).isFalse();
    }

    @Test void missingTargetIsReportedOnlyAfterRevalidatingActorSession() {
        prepare(2L);
        assertThatThrownBy(() -> guard.lock(actor, 2L)).isInstanceOf(ResourceNotFoundException.class);
        verify(sessions).lockByIdAndUserId("sid", 1L);
    }
}
