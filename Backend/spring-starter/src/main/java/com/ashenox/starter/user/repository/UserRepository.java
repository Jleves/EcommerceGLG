package com.ashenox.starter.user.repository;

import com.ashenox.starter.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // A locking read sees current committed membership even under MySQL REPEATABLE READ.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select u from User u
            where u.id = :actorId or u.id = :targetId
               or (u.role = com.ashenox.starter.user.model.Role.SUPER_ADMIN and u.enabled = true)
            order by u.id
            """)
    java.util.List<User> lockAdministrativeUsers(
            @org.springframework.data.repository.query.Param("actorId") Long actorId,
            @org.springframework.data.repository.query.Param("targetId") Long targetId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email = :email")
    Optional<User> lockByEmail(@org.springframework.data.repository.query.Param("email") String email);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
