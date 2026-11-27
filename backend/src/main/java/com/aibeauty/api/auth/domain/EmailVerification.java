package com.aibeauty.api.auth.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가입 이메일 인증 (FIX-008). 이메일당 한 행이며, 코드를 다시 요청하면 같은 행을 새 코드로 덮어쓴다.
 *
 * 상태 흐름: 코드 발송 → (코드 확인 성공) 코드 소진 + 새 이메일이면 가입용 토큰 발급 → (가입) 토큰 사용
 * 코드·토큰은 해시만 저장한다.
 */
@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String codeHash;

	@Column(nullable = false)
	private Instant codeExpiresAt;

	@Column(nullable = false)
	private int failedCount;

	@Column(nullable = false)
	private Instant sentAt;

	@Column(unique = true)
	private String tokenHash;

	private Instant tokenExpiresAt;

	private Instant usedAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	/** 마지막 발송 후 interval이 지났으면 새 코드를 보낼 수 있다 */
	public boolean canResend(Instant now, Duration interval) {
		return !sentAt.plus(interval).isAfter(now);
	}

	/** 새 코드로 덮어쓴다. 실패 횟수와 이전에 발급한 가입용 토큰은 모두 무효가 된다 */
	public void reissueCode(String newCodeHash, Instant codeExpiresAt, Instant now) {
		this.codeHash = newCodeHash;
		this.codeExpiresAt = codeExpiresAt;
		this.failedCount = 0;
		this.sentAt = now;
		this.tokenHash = null;
		this.tokenExpiresAt = null;
		this.usedAt = null;
		this.updatedAt = now;
	}

	public boolean isCodeUsable(Instant now) {
		return codeExpiresAt.isAfter(now);
	}

	public boolean attemptsExceeded(int maxFailures) {
		return failedCount >= maxFailures;
	}

	public void recordFailure(Instant now) {
		this.failedCount++;
		this.updatedAt = now;
	}

	/** 코드를 맞혔다: 코드는 다시 쓸 수 없게 소진한다 */
	public void consumeCode(Instant now) {
		this.codeExpiresAt = now;
		this.updatedAt = now;
	}

	/** 새 이메일이면 가입용 1회용 토큰을 발급한다 */
	public void issueToken(String tokenHash, Instant tokenExpiresAt, Instant now) {
		this.tokenHash = tokenHash;
		this.tokenExpiresAt = tokenExpiresAt;
		this.updatedAt = now;
	}

	public boolean isTokenUsable(Instant now) {
		return tokenHash != null && usedAt == null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
	}

	public void markTokenUsed(Instant now) {
		this.usedAt = now;
		this.updatedAt = now;
	}
}
