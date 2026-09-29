package com.ashenox.starter.auth.passwordchange;

import com.ashenox.starter.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Shared password budget for profile operations. Caller must already hold the user lock. */
@Service
@RequiredArgsConstructor
public class SensitiveOperationPasswordService {
    private final PasswordEncoder encoder;

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = PasswordChangeException.class)
    public void verify(User user, String password) {
        Instant now = Instant.now();
        if (user.getPasswordChangeWindowStart() == null
                || !now.isBefore(user.getPasswordChangeWindowStart().plusSeconds(900))) {
            user.setPasswordChangeWindowStart(now);
            user.setPasswordChangeFailures(0);
        }
        if (user.getPasswordChangeFailures() >= 5) {
            long remaining = Math.max(1, user.getPasswordChangeWindowStart().plusSeconds(900).getEpochSecond()
                    - now.getEpochSecond());
            throw new PasswordChangeException("currentPassword", "Demasiados intentos. Volvé a intentar más tarde.", remaining);
        }
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(password, user.getPasswordHash())) {
            user.setPasswordChangeFailures(user.getPasswordChangeFailures() + 1);
            throw new PasswordChangeException("currentPassword", "La contraseña actual es incorrecta.", 0);
        }
    }
}
