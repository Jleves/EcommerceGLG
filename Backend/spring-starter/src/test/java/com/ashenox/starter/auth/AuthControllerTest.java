package com.ashenox.starter.auth;

import com.ashenox.starter.auth.controller.AuthController;
import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock UserService users;
    @InjectMocks AuthController controller;

    @Test
    void meResolvesIdEvenWhenEmailChangedAfterAuthentication() {
        var principal = new AuthenticatedUser(7L, "old@example.com", "hash", Role.USER, true);
        when(users.findById(7L)).thenReturn(Optional.of(User.builder().id(7L)
                .email("changed@example.com").role(Role.USER).enabled(true).build()));

        var response = controller.me(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(7L);
        assertThat(response.getBody().getEmail()).isEqualTo("changed@example.com");
        verify(users).findById(7L);
        verifyNoMoreInteractions(users);
    }

    @Test
    void meDoesNotFallbackToEmailWhenAccountDisappearsAfterAuthentication() {
        var principal = new AuthenticatedUser(7L, "reassigned@example.com", "hash", Role.USER, true);
        when(users.findById(7L)).thenReturn(Optional.empty());

        assertThat(controller.me(principal).getStatusCode().value()).isEqualTo(404);
        verify(users).findById(7L);
        verifyNoMoreInteractions(users);
    }
}
