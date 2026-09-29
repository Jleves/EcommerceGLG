package com.ashenox.starter.auth.service;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.auth.challenge.service.ChallengeDeliveryService;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.challenge.service.DeliveredChallenge;
import com.ashenox.starter.auth.service.impl.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailMfaService {
    private final ChallengeService challenges;
    private final ChallengeDeliveryService delivery;
    private final AuthService authentication;

    public DeliveredChallenge start(LoginAttempt attempt) {
        return delivery.deliverCreated(attempt.challenge());
    }

    @Transactional(noRollbackFor = ChallengeException.class)
    public IssuedAuthentication verify(String cookie, String code) {
        challenges.requireLoginUser(cookie);
        var challenge = challenges.verifyAndConsume(cookie, ChallengePurpose.LOGIN, null, null, code);
        return authentication.issueSession(challenge.getUser());
    }

    public DeliveredChallenge resend(String cookie) {
        return delivery.resendLogin(cookie);
    }

    public void cancel(String cookie) { challenges.cancel(cookie); }

    public String maskedEmail(String email) {
        int at = email.lastIndexOf('@');
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
