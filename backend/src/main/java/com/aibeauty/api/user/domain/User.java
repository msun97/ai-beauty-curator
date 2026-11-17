package com.aibeauty.api.user.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	/** 행동 로그용 가명 키. 행동 로그에는 user_id 대신 이 값만 남긴다 (NFR-PRIV-05). */
	@Column(nullable = false, unique = true)
	private UUID actorKey;

	@Column(nullable = false)
	private int failedLoginCount;

	private Instant lockedUntil;

	@Column(nullable = false)
	private Instant passwordChangedAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private User(String email, String passwordHash, String nickname, Role role, Instant now) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
		this.role = role;
		this.actorKey = UUID.randomUUID();
		this.failedLoginCount = 0;
		this.passwordChangedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** @param email 소문자로 정규화된 이메일 */
	public static User create(String email, String passwordHash, String nickname, Instant now) {
		return new User(email, passwordHash, nickname, Role.USER, now);
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/** 로그인 실패를 기록하고, 연속 실패가 기준에 도달하면 일정 시간 로그인을 제한한다 (FR-AUTH-02). */
	public void recordLoginFailure(int maxFailures, Duration lockDuration, Instant now) {
		failedLoginCount++;
		if (failedLoginCount >= maxFailures) {
			lockedUntil = now.plus(lockDuration);
			failedLoginCount = 0;
		}
		updatedAt = now;
	}

	public void recordLoginSuccess(Instant now) {
		if (failedLoginCount != 0 || lockedUntil != null) {
			failedLoginCount = 0;
			lockedUntil = null;
			updatedAt = now;
		}
	}

	public void changePassword(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.passwordChangedAt = now;
		this.failedLoginCount = 0;
		this.lockedUntil = null;
		this.updatedAt = now;
	}
}
