package com.ashenox.starter.security.service;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccessSessionValidator {
    private final AuthSessionRepository sessions;

    @Transactional(readOnly = true)
    public void validate(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Access session is required");
        }
        var session = sessions.findWithUserById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid access session"));
        if (!userId.equals(session.getUser().getId()) || !session.getUser().isEnabled()
                || !session.isActiveAt(Instant.now())) {
            throw new IllegalArgumentException("Invalid access session");
        }
    }
}
