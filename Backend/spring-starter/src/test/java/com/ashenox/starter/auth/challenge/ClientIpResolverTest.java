package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.service.ClientIpResolver;
import com.ashenox.starter.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
    private final AppProperties properties = new AppProperties();
    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void takesNearestUntrustedAddressFromTrustedProxyChain() {
        properties.getSecurity().getMfa().setTrustedProxyAddresses(List.of("192.0.2.10", "192.0.2.11"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 203.0.113.8, 192.0.2.11");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.8");
    }
}
