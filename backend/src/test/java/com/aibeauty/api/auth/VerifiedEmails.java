package com.aibeauty.api.auth;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

import com.aibeauty.api.global.security.TokenGenerator;

/**
 * 테스트용: 이메일 인증을 마친 상태(가입용 토큰 발급됨)를 DB에 바로 만든다 (FIX-008).
 * 인증 메일 흐름 자체를 확인하는 테스트가 아니라 "가입된 사용자"가 필요한 테스트에서 쓴다.
 * 인증 흐름은 EmailVerificationApiIntegrationTest와 AuthIntegrationTest의 전체 가입 흐름에서 확인한다.
 */
public final class VerifiedEmails {

	private VerifiedEmails() {
	}

	/** @return 가입 요청에 넣을 verificationToken */
	public static String issueToken(JdbcTemplate jdbc, String email) {
		String token = TokenGenerator.newToken();
		Instant now = Instant.now();
		jdbc.update("""
				INSERT INTO email_verifications
				    (email, code_hash, code_expires_at, failed_count, sent_at, token_hash, token_expires_at, created_at, updated_at)
				VALUES (?, repeat('0', 64), ?, 0, ?, ?, ?, ?, ?)
				ON CONFLICT (email) DO UPDATE SET token_hash = EXCLUDED.token_hash,
				    token_expires_at = EXCLUDED.token_expires_at, used_at = NULL, updated_at = EXCLUDED.updated_at
				""", email.trim().toLowerCase(), Timestamp.from(now), Timestamp.from(now), TokenGenerator.hash(token),
				Timestamp.from(now.plus(Duration.ofMinutes(30))), Timestamp.from(now), Timestamp.from(now));
		return token;
	}

	/** 가입 요청 본문 (모든 약관 동의) */
	public static String signupBody(String token, String password) {
		return """
				{"verificationToken":"%s","password":"%s","agreements":{"terms":true,"privacy":true,"skinData":true}}
				""".formatted(token, password);
	}
}
