package com.ashenox.starter.auth.passwordreset.repository;

import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByUser(User user);

    // Current read after administrative coordination, including tokens created after a prior snapshot.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from PasswordResetToken t where t.user.id = :userId")
    Optional<PasswordResetToken> lockByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
