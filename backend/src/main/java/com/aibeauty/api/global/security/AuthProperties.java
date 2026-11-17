package com.aibeauty.api.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 관련 설정 (ADR-004). 운영 환경에서는 JWT_SECRET 환경 변수가 반드시 필요하다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, Cookie cookie, Login login, PasswordReset passwordReset, String termsVersion) {

	public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
		public Jwt {
			if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
				throw new IllegalStateException("app.auth.jwt.secret(JWT_SECRET)는 32바이트 이상이어야 합니다.");
			}
		}
	}

	/** secure: HTTPS 환경에서만 쿠키 전송. 로컬(http) 개발 시에만 false로 둔다. */
	public record Cookie(boolean secure) {
	}

	public record Login(int maxFailures, Duration lockDuration) {
	}

	/** linkBaseUrl: 메일에 넣을 프론트엔드 비밀번호 재설정 화면 주소 */
	public record PasswordReset(Duration tokenTtl, String linkBaseUrl) {
	}
}
