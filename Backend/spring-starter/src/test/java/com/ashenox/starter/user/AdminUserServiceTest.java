package com.ashenox.starter.user;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.impl.AdminUserServiceImpl;
import com.ashenox.starter.user.service.AdminActor;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminUserServiceTest {
    @Test
    void rejectsNonSuperAdminEvenWhenCalledWithoutHttpSecurity() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl(repository, encoder, mock(org.springframework.context.ApplicationEventPublisher.class), mock(com.ashenox.starter.user.service.AdminMutationGuard.class), mock(com.ashenox.starter.auth.challenge.service.ChallengeService.class), mock(com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository.class), mock(com.ashenox.starter.auth.session.service.AuthSessionService.class), mock(jakarta.validation.Validator.class));
        for (Role actor : new Role[]{Role.ADMIN, Role.USER, null}) {
            for (Role target : Role.values()) {
                assertThatThrownBy(() -> service.create(
                        new CreateUserRequest("new@example.com", "initial-password", target), new AdminActor(1L, actor, "sid")))
                        .isInstanceOf(AccessDeniedException.class);
            }
        }
        verifyNoInteractions(repository, encoder);
    }

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(repository, encoder, mock(org.springframework.context.ApplicationEventPublisher.class), mock(com.ashenox.starter.user.service.AdminMutationGuard.class), mock(com.ashenox.starter.auth.challenge.service.ChallengeService.class), mock(com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository.class), mock(com.ashenox.starter.auth.session.service.AuthSessionService.class), mock(jakarta.validation.Validator.class));
    private final AdminActor actor = new AdminActor(1L, Role.SUPER_ADMIN, "sid");

    @Test
    void deniesEveryOperationForMissingOrUntrustedActorContext() {
        for (AdminActor invalid : new AdminActor[]{null, new AdminActor(1L, Role.USER, "sid"),
                new AdminActor(1L, Role.ADMIN, "sid"), new AdminActor(null, Role.SUPER_ADMIN, "sid"),
                new AdminActor(0L, Role.SUPER_ADMIN, "sid"), new AdminActor(1L, Role.SUPER_ADMIN, null),
                new AdminActor(1L, Role.SUPER_ADMIN, " ")}) {
            assertThatThrownBy(() -> service.list(0, 20, invalid)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.getById(2L, invalid)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateEmail(2L, new com.ashenox.starter.user.dto.UpdateUserEmailRequest("new@example.com"), invalid))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.create(new CreateUserRequest("new@example.com", "initial-password", Role.USER), invalid))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(repository, encoder);
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        for (int[] values : new int[][]{{-1, 20}, {0, 0}, {0, -1}, {0, 101}}) {
            assertThatThrownBy(() -> service.list(values[0], values[1], actor)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void mapsPageWithFixedOrderAndSafeUserState() {
        var pageable = PageRequest.of(1, 100, Sort.by("id").ascending());
        var user = User.builder().id(101L).email("disabled@example.com").role(Role.USER)
                .enabled(false).emailMfaEnabled(true).passwordHash("secret").build();
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 101));
        var result = service.list(1, 100, actor);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalElements()).isEqualTo(101);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content().getFirst().id()).isEqualTo(101L);
        assertThat(result.content().getFirst().enabled()).isFalse();
        assertThat(result.content().getFirst().mfaEnabled()).isTrue();
        verify(repository).findAll(pageable);
    }

    @Test
    void returnsEmptyPageAndMissingDetail() {
        var pageable = PageRequest.of(0, 20, Sort.by("id").ascending());
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        assertThat(service.list(0, 20, actor).content()).isEmpty();
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(999L, actor)).isInstanceOf(ResourceNotFoundException.class);
    }
}
