package com.aibeauty.api.auth.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

	/**
	 * 행이 없으면 "아직 보낸 적 없음" 상태로 만든다 (만료된 빈 코드, 발송 시각 1970년).
	 * 같은 이메일로 동시에 요청해도 충돌 없이 한 행만 생기게 하려고 ON CONFLICT DO NOTHING을 쓴다.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO email_verifications (email, code_hash, code_expires_at, failed_count, sent_at, created_at, updated_at)
			VALUES (:email, repeat('0', 64), :now, 0, 'epoch'::timestamptz, :now, :now)
			ON CONFLICT (email) DO NOTHING
			""", nativeQuery = true)
	void insertIfAbsent(@Param("email") String email, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from EmailVerification v where v.email = :email")
	Optional<EmailVerification> findByEmailForUpdate(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from EmailVerification v where v.tokenHash = :tokenHash")
	Optional<EmailVerification> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	/** 정리: 코드와 토큰이 모두 만료됐거나 토큰을 이미 쓴 행 중 마지막 변경이 staleBefore보다 이전인 행 */
	@Modifying
	@Query("""
			delete from EmailVerification v
			where v.updatedAt < :staleBefore
			  and v.codeExpiresAt <= :now
			  and (v.tokenExpiresAt is null or v.tokenExpiresAt <= :now or v.usedAt is not null)
			""")
	int deleteStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
