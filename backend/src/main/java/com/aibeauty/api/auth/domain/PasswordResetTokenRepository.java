package com.aibeauty.api.auth.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :tokenHash")
	Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	/** 새 링크를 발급하면 이전에 보낸 링크는 쓸 수 없게 한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
	int invalidateAllByUser(@Param("userId") Long userId, @Param("now") Instant now);
}
