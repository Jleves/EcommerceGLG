package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.shared.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {
    private final AppProperties properties;

    public String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String peer = canonicalLiteral(request.getRemoteAddr());
        if (peer == null) return null;
        Set<String> trusted = properties.getSecurity().getMfa().getTrustedProxyAddresses().stream()
                .map(this::canonicalLiteral)
                .filter(value -> value != null)
                .collect(Collectors.toSet());
        if (!trusted.contains(peer)) return peer;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer;
        String[] hops = forwarded.split(",", -1);
        String client = peer;
        for (int i = hops.length - 1; i >= 0 && trusted.contains(client); i--) {
            String candidate = canonicalLiteral(hops[i].trim());
            if (candidate == null) return peer;
            client = candidate;
        }
        return client;
    }

    private String canonicalLiteral(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
