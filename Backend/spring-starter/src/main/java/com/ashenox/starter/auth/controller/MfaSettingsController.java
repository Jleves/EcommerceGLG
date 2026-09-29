package com.ashenox.starter.auth.controller;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeDeliveryService;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.service.MfaSettingsService;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.security.model.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
public class MfaSettingsController {
    private final MfaSettingsService settings;
    private final ChallengeDeliveryService delivery;
    private final AuthCookieService cookies;

    @GetMapping("/status")
    public MfaSettingsService.SettingsStatus status(@AuthenticationPrincipal AuthenticatedUser user,
                                                   HttpServletRequest request) {
        return settings.status(user.id(), sessionId(request));
    }

    @PostMapping("/enable/start")
    public ResponseEntity<AuthController.MfaTimingResponse> enableStart(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartRequest body, HttpServletRequest request) {
        return start(user, body, request, ChallengePurpose.ENABLE);
    }

    @PostMapping("/disable/start")
    public ResponseEntity<AuthController.MfaTimingResponse> disableStart(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartRequest body, HttpServletRequest request) {
        return start(user, body, request, ChallengePurpose.DISABLE);
    }

    @PostMapping("/enable/confirm")
    public ResponseEntity<Void> enableConfirm(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AuthController.MfaCodeRequest body, HttpServletRequest request) {
        return confirm(user, body, request, ChallengePurpose.ENABLE);
    }

    @PostMapping("/disable/confirm")
    public ResponseEntity<Void> disableConfirm(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody AuthController.MfaCodeRequest body, HttpServletRequest request) {
        return confirm(user, body, request, ChallengePurpose.DISABLE);
    }

    @PostMapping("/settings/resend")
    public ResponseEntity<AuthController.MfaTimingResponse> resend(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  HttpServletRequest request) {
        String cookie = cookies.readMfaChallenge(request).orElse(null);
        var prepared = settings.resend(user.id(), sessionId(request), cookie);
        var sent = delivery.deliverResent(cookie, prepared.purpose(), prepared.result());
        return ResponseEntity.accepted().body(new AuthController.MfaTimingResponse(sent.expiresAt(), sent.resendAvailableAt()));
    }

    private ResponseEntity<AuthController.MfaTimingResponse> start(AuthenticatedUser user, StartRequest body,
                                                                  HttpServletRequest request, ChallengePurpose purpose) {
        var prepared = settings.start(user.id(), sessionId(request), purpose, body.currentPassword());
        var sent = delivery.deliverCreated(prepared);
        return ResponseEntity.accepted()
                .header(HttpHeaders.SET_COOKIE, cookies.mfaChallenge(sent.cookieValue(), sent.expiresAt()).toString())
                .body(new AuthController.MfaTimingResponse(sent.expiresAt(), sent.resendAvailableAt()));
    }

    private ResponseEntity<Void> confirm(AuthenticatedUser user, AuthController.MfaCodeRequest body,
                                          HttpServletRequest request, ChallengePurpose purpose) {
        settings.confirm(user.id(), sessionId(request), purpose, cookies.readMfaChallenge(request).orElse(null), body.stringCode());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.deleteAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.deleteRefreshToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.deleteXsrfToken().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.deleteMfaChallenge().toString()).build();
    }

    private String sessionId(HttpServletRequest request) {
        // This attribute is written only after JWT validation, never read from body or headers.
        return (String) request.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE);
    }

    public record StartRequest(@NotBlank @Size(max = 72) String currentPassword) {
        @Override public String toString() { return "StartRequest[redacted]"; }
    }
}
