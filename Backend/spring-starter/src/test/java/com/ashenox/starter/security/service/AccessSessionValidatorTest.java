package com.ashenox.starter.security.service;

import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessSessionValidatorTest {
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AccessSessionValidator validator = new AccessSessionValidator(sessions);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingSidWithoutQuery(String sid) {
        assertThatThrownBy(() -> validator.validate(1L, sid)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign", "revoked", "expired", "disabled", "active"})
    void checksOwnerAccountAndSession(String scenario) {
        AuthSession session = new AuthSession();
        session.setUser(User.builder().id(scenario.equals("foreign") ? 2L : 1L)
                .enabled(!scenario.equals("disabled")).build());
        session.setExpiresAt(Instant.now().plusSeconds(scenario.equals("expired") ? -1 : 60));
        if (scenario.equals("revoked")) session.setRevokedAt(Instant.now());
        when(sessions.findWithUserById("sid"))
                .thenReturn(scenario.equals("missing") ? Optional.empty() : Optional.of(session));
        if (scenario.equals("active")) assertThatCode(() -> validator.validate(1L, "sid")).doesNotThrowAnyException();
        else assertThatThrownBy(() -> validator.validate(1L, "sid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseFailureNeverBecomesSuccessfulValidation() {
        when(sessions.findWithUserById("sid")).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(() -> validator.validate(1L, "sid")).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
