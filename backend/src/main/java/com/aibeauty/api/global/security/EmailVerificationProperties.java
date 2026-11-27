package com.aibeauty.api.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가입 이메일 인증 설정 (FIX-008, app.auth.email-verification).
 * codeTtl: 인증 코드 유효 시간, tokenTtl: 코드 확인 후 가입용 토큰 유효 시간,
 * maxFailures: 코드 입력 실패 허용 횟수(넘으면 새 코드 필요), resendInterval: 코드 재발송 최소 간격
 */
@ConfigurationProperties(prefix = "app.auth.email-verification")
public record EmailVerificationProperties(Duration codeTtl, Duration tokenTtl, int maxFailures,
		Duration resendInterval) {
	public EmailVerificationProperties {
		if (codeTtl == null || tokenTtl == null || resendInterval == null || maxFailures < 1) {
			throw new IllegalStateException("app.auth.email-verification 설정(code-ttl, token-ttl, max-failures, resend-interval)이 필요합니다.");
		}
	}
}
