package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.auth.challenge.repository.AuthRateLimitRepository;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ChallengeCleanupService {
    private final AuthChallengeRepository challenges;
    private final AuthRateLimitRepository counters;
    private final AppProperties properties;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    public void removeExpiredRecords() {
        Instant now = Instant.now();
        challenges.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        var config = properties.getSecurity().getMfa();
        Duration longestWindow = config.getAggregateWindow().compareTo(config.getLoginWindow()) >= 0
                ? config.getAggregateWindow() : config.getLoginWindow();
        counters.deleteStaleBefore(now.minus(longestWindow).minus(Duration.ofDays(1)));
    }
}
