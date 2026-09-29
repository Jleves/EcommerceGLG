package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.auth.challenge.repository.AuthRateLimitRepository;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.service.impl.AuthService;
import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.shared.error.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LoginRateLimitIntegrationTest {
    @Autowired private AuthService authService;
    @Autowired private AuthRateLimitRepository limits;

    @Test
    void unknownAccountFailuresPersistAndEventuallyLimitLogin() {
        String email = "unknown-" + UUID.randomUUID() + "@example.com";
        LoginRequest request = new LoginRequest(email, "wrong-password");
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(limits.findByScopeAndSubjectKey(AuthRateLimitScope.LOGIN_ACCOUNT, email)
                .orElseThrow().getAttemptCount()).isEqualTo(10);
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.AUTH_LOGIN_RATE_LIMITED);
    }

    @Test
    void ipLimitIsSharedAcrossDifferentUnknownAccounts() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("203.0.113.99");
        for (int attempt = 0; attempt < 30; attempt++) {
            LoginRequest request = new LoginRequest("ip-" + UUID.randomUUID() + "@example.com", "wrong-password");
            assertThatThrownBy(() -> authService.login(request, servletRequest))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(limits.findByScopeAndSubjectKey(AuthRateLimitScope.LOGIN_IP, "203.0.113.99")
                .orElseThrow().getAttemptCount()).isEqualTo(30);
        LoginRequest anotherAccount = new LoginRequest("ip-" + UUID.randomUUID() + "@example.com", "wrong-password");
        assertThatThrownBy(() -> authService.login(anotherAccount, servletRequest))
                .isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.AUTH_LOGIN_RATE_LIMITED);
    }
}
