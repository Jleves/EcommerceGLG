package com.ashenox.starter.security.jwt;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.security.service.DatabaseUserDetailsService;
import com.ashenox.starter.security.service.AccessSessionValidator;
import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.shared.error.ApiErrorResponder;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

class JwtRequestFilterTest {

    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final AccessSessionValidator sessionValidator = mock(AccessSessionValidator.class);
    private final AuthCookieService cookieService = new AuthCookieService(properties());
    private final JwtRequestFilter filter = new JwtRequestFilter(
            userDetailsService, jwtUtil, new ApiErrorResponder(new ObjectMapper()), cookieService, sessionValidator);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSharedContractForExpiredAccessCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_TOKEN, "expired-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.extractUserId("expired-token")).thenThrow(mock(ExpiredJwtException.class));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_EXPIRED", "requestId", "path");
        verifyNoInteractions(userDetailsService);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void returnsSharedContractForInvalidAccessCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_TOKEN, "invalid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.extractUserId("invalid-token")).thenThrow(new IllegalArgumentException("token details"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("AUTH_TOKEN_INVALID", "requestId", "path")
                .doesNotContain("token details");
        verifyNoInteractions(userDetailsService);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void ignoresBearerAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer ignored-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtUtil, userDetailsService);
    }

    @Test
    void authenticatesByIdAndLogsCurrentIdentityAndSession() throws Exception {
        var principal = new AuthenticatedUser(7L, "changed@example.com", "hash", Role.ADMIN, true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn(7L);
        when(jwtUtil.extractSessionId("valid-token")).thenReturn("session-7");
        when(userDetailsService.loadUserById(7L)).thenReturn(principal);
        when(jwtUtil.isTokenValid("valid-token", principal)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_TOKEN, "valid-token"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        assertThat(request.getAttribute(RequestLoggingFilter.USER_ID_ATTRIBUTE)).isEqualTo(7L);
        assertThat(request.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)).isEqualTo("session-7");
        verify(sessionValidator).validate(7L, "session-7");
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "mismatch"})
    void rejectsUnavailableOrMismatchedIdentity(String scenario) throws Exception {
        when(jwtUtil.extractUserId("token")).thenReturn(7L);
        if (scenario.equals("missing")) {
            when(userDetailsService.loadUserById(7L)).thenThrow(new UsernameNotFoundException("not found"));
        } else {
            var principal = new AuthenticatedUser(scenario.equals("mismatch") ? 8L : 7L,
                    "user@example.com", "hash", Role.USER, !scenario.equals("disabled"));
            when(userDetailsService.loadUserById(7L)).thenReturn(principal);
            if (scenario.equals("mismatch")) when(jwtUtil.isTokenValid("token", principal)).thenReturn(false);
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_TOKEN, "token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_TOKEN_INVALID");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getCookies().setSecure(false);
        return properties;
    }
}
