package com.aibeauty.api.auth.domain;

import java.time.Instant;

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
@Table(name = "user_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConsentType consentType;

	/** 동의한 약관 버전. 약관이 바뀌면 어떤 버전에 동의했는지 확인할 수 있다. */
	@Column(nullable = false)
	private String termsVersion;

	@Column(nullable = false)
	private Instant agreedAt;

	private Instant withdrawnAt;

	private UserConsent(User user, ConsentType consentType, String termsVersion, Instant agreedAt) {
		this.user = user;
		this.consentType = consentType;
		this.termsVersion = termsVersion;
		this.agreedAt = agreedAt;
	}

	public static UserConsent agree(User user, ConsentType type, String termsVersion, Instant now) {
		return new UserConsent(user, type, termsVersion, now);
	}
}
