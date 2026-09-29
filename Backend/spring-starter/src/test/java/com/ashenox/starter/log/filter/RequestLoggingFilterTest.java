package com.ashenox.starter.log.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void mfaRequestLogsExcludeCredentialsCookiesAndBody() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            var request = new MockHttpServletRequest("POST", "/api/auth/mfa/login/verify");
            request.setContentType("application/json");
            request.setContent("{\"code\":\"004271\",\"currentPassword\":\"private-password\"}"
                    .getBytes(StandardCharsets.UTF_8));
            request.setCookies(new Cookie("MFA_CHALLENGE", "private-challenge-secret"));
            request.addHeader("Authorization", "Bearer private-access-token");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(captured.list).isNotEmpty();
            assertThat(captured.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .doesNotContain("004271", "private-password", "private-challenge-secret", "private-access-token"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void createsAFullRequestIdAndReturnsItInTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(RequestLoggingFilter.getRequestId(request)).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
    }
}
