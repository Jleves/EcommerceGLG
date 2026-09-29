package com.ashenox.starter.auth.passwordchange;

import com.ashenox.starter.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordUpdateService passwords;
    private final SensitiveOperationPasswordService currentPasswords;

    // Persist failed attempts across instances; expected rejection must not roll them back.
    @Transactional(noRollbackFor = PasswordChangeException.class)
    public void change(Long userId, ChangePasswordRequest request) {
        var user = users.lockById(userId).orElseThrow(() -> new AccessDeniedException("Cuenta no disponible"));
        if (!user.isEnabled()) throw new AccessDeniedException("Cuenta no disponible");
        currentPasswords.verify(user, request.currentPassword());
        try {
            PasswordUpdateService.validate(request.newPassword());
        } catch (com.ashenox.starter.shared.error.BusinessException exception) {
            throw new PasswordChangeException("newPassword", exception.getMessage(), 0);
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new PasswordChangeException("newPassword", "La contraseña nueva debe ser diferente de la actual.", 0);
        }
        passwords.update(user, request.newPassword());
    }
}
