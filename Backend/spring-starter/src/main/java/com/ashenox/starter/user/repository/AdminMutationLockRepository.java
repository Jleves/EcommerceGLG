package com.ashenox.starter.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AdminMutationLockRepository {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // Flyway creates this row. Missing coordination must fail closed, never insert lazily.
        jdbc.queryForObject("select id from admin_mutation_lock where id = 1 for update", Integer.class);
    }
}
