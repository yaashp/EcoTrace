package com.ecotrace.repo;

import com.ecotrace.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from UserSession s where s.tokenHash = :hash")
    void deleteByHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.user.id = :userId")
    void deleteForUser(@Param("userId") Long userId);
}
