package com.aibeauty.api.auth.domain;

import java.time.Instant;
import java.util.UUID;

import com.aibeauty.api.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	/** 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다. */
	@Column(nullable = false, unique = true)
	private String tokenHash;

	/** 한 번의 로그인에서 재발급으로 이어진 토큰 묶음. 탈취 감지 시 묶음 전체를 폐기한다. */
	@Column(nullable = false)
	private UUID familyId;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant usedAt;

	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	private RevokedReason revokedReason;

	@Column(nullable = false)
	private Instant createdAt;

	private RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt, Instant now) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	public static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant expiresAt, Instant now) {
		return new RefreshToken(user, tokenHash, familyId, expiresAt, now);
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}

	public void revoke(RevokedReason reason, Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
			this.revokedReason = reason;
		}
	}
}
