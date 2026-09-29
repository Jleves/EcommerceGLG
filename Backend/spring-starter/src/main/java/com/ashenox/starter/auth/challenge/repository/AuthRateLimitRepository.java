package com.ashenox.starter.auth.challenge.repository;

import com.ashenox.starter.auth.challenge.model.AuthRateLimit;
import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface AuthRateLimitRepository extends JpaRepository<AuthRateLimit, Long> {
    @Lock(PESSIMISTIC_WRITE)
    @Query("select counter from AuthRateLimit counter where counter.scope = :scope and counter.subjectKey = :subjectKey")
    Optional<AuthRateLimit> lockByScopeAndSubjectKey(@Param("scope") AuthRateLimitScope scope,
                                                     @Param("subjectKey") String subjectKey);

    Optional<AuthRateLimit> findByScopeAndSubjectKey(AuthRateLimitScope scope, String subjectKey);

    @Modifying
    @Query("delete from AuthRateLimit counter where counter.windowStartedAt < :cutoff")
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
