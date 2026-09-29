package com.ashenox.starter.auth.challenge.repository;

import com.ashenox.starter.auth.challenge.model.AuthChallenge;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, String> {
    @Query("select challenge.user.email from AuthChallenge challenge where challenge.id = :id")
    Optional<String> findRecipientByChallengeId(@Param("id") String id);

    @Query("select challenge.user.id from AuthChallenge challenge where challenge.id = :id")
    Optional<Long> findUserIdByChallengeId(@Param("id") String id);

    @Lock(PESSIMISTIC_WRITE)
    @Query("select challenge from AuthChallenge challenge where challenge.id = :id")
    Optional<AuthChallenge> lockById(@Param("id") String id);

    @Lock(PESSIMISTIC_WRITE)
    @Query("select challenge from AuthChallenge challenge where challenge.user.id = :userId and challenge.purpose = :purpose and challenge.consumedAt is null and challenge.invalidatedAt is null")
    List<AuthChallenge> lockUnconsumedByUserAndPurpose(@Param("userId") Long userId,
                                                       @Param("purpose") ChallengePurpose purpose);

    @Lock(PESSIMISTIC_WRITE)
    @Query("select challenge from AuthChallenge challenge where challenge.user.id = :userId and challenge.consumedAt is null and challenge.invalidatedAt is null")
    List<AuthChallenge> lockUnconsumedByUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AuthChallenge challenge where challenge.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
