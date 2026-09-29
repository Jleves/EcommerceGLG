package com.ashenox.starter.security.service;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(repository);

    @Test
    void resolvesImmutableIdAndPreservesCurrentEmailRoleAndDisabledState() {
        when(repository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L)
                .email("new@example.com").passwordHash("hash").role(Role.ADMIN).enabled(false).build()));

        AuthenticatedUser principal = service.loadUserById(7L);

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("new@example.com");
        assertThat(principal.role()).isEqualTo(Role.ADMIN);
        assertThat(principal.isEnabled()).isFalse();
        verify(repository).findById(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectsMissingIdWithoutFallback() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserById(7L)).isInstanceOf(UsernameNotFoundException.class);
        verify(repository).findById(7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void passwordLoginStillResolvesNormalizedEmail() {
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(User.builder().id(7L)
                .email("user@example.com").passwordHash("hash").role(Role.USER).enabled(true).build()));

        assertThat(service.loadUserByUsername("  USER@EXAMPLE.COM  ").getUsername()).isEqualTo("user@example.com");
        verify(repository).findByEmail("user@example.com");
        verifyNoMoreInteractions(repository);
    }
}
