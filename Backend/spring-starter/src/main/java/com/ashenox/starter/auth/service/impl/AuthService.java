package com.ashenox.starter.auth.service.impl;

import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.auth.service.IssuedAuthentication;
import com.ashenox.starter.auth.service.LoginAttempt;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.user.repository.UserRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.auth.session.service.IssuedSession;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.dto.UserDTO;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.challenge.service.LoginRateLimitService;
import com.ashenox.starter.user.support.EmailNormalizer;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JWTUtil jwtUtil;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final LoginRateLimitService loginRateLimits;
    private final ChallengeService challenges;

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, ChallengeException.class})
    public LoginAttempt login(LoginRequest loginRequest) {
        return loginInternal(loginRequest, null);
    }

    @Transactional(noRollbackFor = {InvalidCredentialsException.class, ChallengeException.class})
    public LoginAttempt login(LoginRequest loginRequest, HttpServletRequest request) {
        return loginInternal(loginRequest, request);
    }

    private LoginAttempt loginInternal(LoginRequest loginRequest, HttpServletRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(loginRequest.getEmail());
        userRepository.lockByEmail(normalizedEmail);
        var budget = loginRateLimits.lock(normalizedEmail, request);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            User user = userService.findByEmail(principal.getUsername()).orElseThrow();
            if (user.isEmailMfaEnabled()) {
                var created = challenges.start(user.getId(), ChallengePurpose.LOGIN, null);
                return new LoginAttempt(null, created, user.getEmail());
            }
            return new LoginAttempt(issueSession(user), null, null);
        } catch (AuthenticationException exception) {
            budget.recordFailure();
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedAuthentication issueSession(User user) {
        IssuedSession session = authSessionService.createSession(user);
        String id = session.session().getId();
        return new IssuedAuthentication(UserDTO.fromUser(user),
                jwtUtil.generateToken(user.getId(), id),
                session.refreshToken(), id);
    }
}
