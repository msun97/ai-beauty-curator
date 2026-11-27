package com.aibeauty.api.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 관련 설정 (ADR-004). 운영 환경에서는 JWT_SECRET 환경 변수가 반드시 필요하다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, Cookie cookie, Login login, PasswordReset passwordReset, String termsVersion) {

	/**
	 * refreshReuseGrace: 이미 사용된 Refresh Token이 다시 와도 탈취가 아닌 동시 요청으로 봐줄 시간 (FIX-003).
	 * 화면 미리 불러오기·여러 탭에서 재발급이 겹칠 때 정상 사용자가 로그아웃되지 않게 한다.
	 * 0이면 유예 없이 재사용을 바로 탈취로 판단한다(가장 엄격). 설정하지 않으면 0으로 본다.
	 */
	public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl,
			Duration refreshReuseGrace) {
		public Jwt {
			if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
				throw new IllegalStateException("app.auth.jwt.secret(JWT_SECRET)는 32바이트 이상이어야 합니다.");
			}
			if (refreshReuseGrace == null) {
				refreshReuseGrace = Duration.ZERO;
			}
			if (refreshReuseGrace.isNegative()) {
				throw new IllegalStateException("app.auth.jwt.refresh-reuse-grace는 0 이상이어야 합니다.");
			}
		}
	}

	/** secure: HTTPS 환경에서만 쿠키 전송. 로컬(http) 개발 시에만 false로 둔다. */
	public record Cookie(boolean secure) {
	}

	/**
	 * 로그인 실패 제한 (FR-AUTH-02, FIX-006).
	 * emailKeySecret: 실패 기록을 이메일 원문 대신 HMAC 값으로 저장할 때 쓰는 비밀키 (LOGIN_EMAIL_KEY_SECRET).
	 * 가입하지 않은 이메일도 기록하므로 원문을 남기지 않는다. JWT 서명 키와는 따로 둔다.
	 */
	public record Login(int maxFailures, Duration lockDuration, String emailKeySecret) {
		public Login {
			if (emailKeySecret == null || emailKeySecret.getBytes(StandardCharsets.UTF_8).length < 32) {
				throw new IllegalStateException(
						"app.auth.login.email-key-secret(LOGIN_EMAIL_KEY_SECRET)는 32바이트 이상이어야 합니다.");
			}
		}
	}

	/** linkBaseUrl: 메일에 넣을 프론트엔드 비밀번호 재설정 화면 주소 */
	public record PasswordReset(Duration tokenTtl, String linkBaseUrl) {
	}
}
