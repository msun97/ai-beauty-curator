package com.aibeauty.api.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/** 같은 토큰으로 동시에 재발급을 요청해도 한 번만 처리되도록 행 잠금을 건다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason
			where t.familyId = :familyId and t.revokedAt is null""")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") RevokedReason reason,
			@Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason
			where t.user.id = :userId and t.revokedAt is null""")
	int revokeAllByUser(@Param("userId") Long userId, @Param("reason") RevokedReason reason,
			@Param("now") Instant now);
}
