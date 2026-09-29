package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.shared.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {
    private final RateLimitService limits;
    private final ClientIpResolver clientIps;
    private final AppProperties properties;

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ChallengeException.class)
    public AttemptBudget lock(String normalizedEmail, HttpServletRequest request) {
        Instant now = Instant.now();
        var config = properties.getSecurity().getMfa();
        var account = limits.lock(AuthRateLimitScope.LOGIN_ACCOUNT, normalizedEmail,
                config.getLoginWindow(), now);
        String ip = clientIps.resolve(request);
        var ipLimit = ip == null ? null : limits.lock(AuthRateLimitScope.LOGIN_IP, ip,
                config.getLoginWindow(), now);
        if (account.exhausted(config.getMaxLoginAttemptsPerAccount())) {
            throw limited(account.retryAfterSeconds());
        }
        if (ipLimit != null && ipLimit.exhausted(config.getMaxLoginAttemptsPerIp())) {
            throw limited(ipLimit.retryAfterSeconds());
        }
        return new AttemptBudget(account, ipLimit);
    }

    private ChallengeException limited(long seconds) {
        return new ChallengeException(ApiErrorCode.AUTH_LOGIN_RATE_LIMITED,
                "Demasiados intentos de inicio de sesión. Volvé a intentar más tarde.", seconds);
    }

    public record AttemptBudget(RateLimitService.RateLimitState account,
                                RateLimitService.RateLimitState ip) {
        public void recordFailure() {
            account.increment();
            if (ip != null) ip.increment();
        }
    }
}
