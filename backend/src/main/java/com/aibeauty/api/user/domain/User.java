package com.aibeauty.api.user.domain;

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
		this.passwordChangedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** @param email 소문자로 정규화된 이메일 */
	public static User create(String email, String passwordHash, String nickname, Instant now) {
		return new User(email, passwordHash, nickname, Role.USER, now);
	}

	public void changePassword(String newPasswordHash, Instant now) {
		this.passwordHash = newPasswordHash;
		this.passwordChangedAt = now;
		this.updatedAt = now;
	}
}
