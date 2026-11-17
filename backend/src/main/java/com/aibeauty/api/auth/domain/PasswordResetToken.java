package com.aibeauty.api.auth.domain;

import java.time.Instant;

import com.aibeauty.api.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant usedAt;

	@Column(nullable = false)
	private Instant createdAt;

	private PasswordResetToken(User user, String tokenHash, Instant expiresAt, Instant now) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	public static PasswordResetToken issue(User user, String tokenHash, Instant expiresAt, Instant now) {
		return new PasswordResetToken(user, tokenHash, expiresAt, now);
	}

	/** 사용 전이고 만료되지 않은 토큰만 유효하다 (30분, 1회 사용 - FR-AUTH-05). */
	public boolean isUsable(Instant now) {
		return usedAt == null && expiresAt.isAfter(now);
	}

	public void markUsed(Instant now) {
		this.usedAt = now;
	}
}
