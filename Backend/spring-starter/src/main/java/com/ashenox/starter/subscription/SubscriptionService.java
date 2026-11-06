package com.ashenox.starter.subscription;

import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.auth.challenge.service.ClientIpResolver;
import com.ashenox.starter.auth.challenge.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository repository;
    private final RateLimitService limits;
    private final ClientIpResolver clientIps;

    @Transactional(noRollbackFor = SubscriptionRateLimitedException.class)
    public void subscribe(SubscriptionRequest request, HttpServletRequest httpRequest) {
        String contact = normalize(request.channel(), request.contact());
        Instant now = Instant.now();
        String ip = clientIps.resolve(httpRequest);
        if (ip != null) {
            var budget = limits.lock(AuthRateLimitScope.SUBSCRIPTION_IP, ip, Duration.ofHours(1), now);
            if (budget.exhausted(10)) throw new SubscriptionRateLimitedException(budget.retryAfterSeconds());
            budget.increment();
        }
        repository.subscribe(request.channel(), contact, now);
    }

    static String normalize(SubscriptionRequest.Channel channel, String raw) {
        if (channel == null || raw == null) throw new SubscriptionValidationException("Canal y contacto son obligatorios.");
        if (channel == SubscriptionRequest.Channel.EMAIL) {
            String email = raw.strip().toLowerCase(Locale.ROOT);
            if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                throw new SubscriptionValidationException("Ingresá un email válido.");
            return email;
        }
        String value = raw.strip().replaceAll("[\\s()\\-]", "");
        if (value.startsWith("+549")) value = value.substring(4);
        if (!value.matches("[1-9][0-9]{9}"))
            throw new SubscriptionValidationException("Ingresá un celular argentino de diez dígitos, con o sin +54 9.");
        return "+549" + value;
    }
}
