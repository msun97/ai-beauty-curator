package com.aibeauty.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aibeauty.api.global.security.AuthProperties;

class LoginEmailKeyTest {

	private static final String SECRET_A = "secret-a-0123456789-0123456789-abcdef";
	private static final String SECRET_B = "secret-b-0123456789-0123456789-abcdef";

	private static LoginEmailKey keyWith(String secret) {
		return new LoginEmailKey(new AuthProperties(
				new AuthProperties.Jwt("jwt-secret-0123456789-0123456789-abcdef", "test", Duration.ofMinutes(15),
						Duration.ofDays(14), Duration.ZERO),
				new AuthProperties.Cookie(false),
				new AuthProperties.Login(5, Duration.ofMinutes(10), secret),
				new AuthProperties.PasswordReset(Duration.ofMinutes(30), "http://localhost/reset"),
				"2026-11"));
	}

	@Test
	@DisplayName("16진수 64자이고 이메일 원문이 들어 있지 않다")
	void hexWithoutEmail() {
		String key = keyWith(SECRET_A).of("someone@example.com");
		assertThat(key).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("someone");
	}

	@Test
	@DisplayName("대소문자·앞뒤 공백이 달라도 같은 이메일이면 같은 키")
	void normalized() {
		LoginEmailKey emailKey = keyWith(SECRET_A);
		assertThat(emailKey.of("  Someone@Example.COM ")).isEqualTo(emailKey.of("someone@example.com"));
	}

	@Test
	@DisplayName("다른 이메일은 다른 키, 같은 이메일도 비밀키가 다르면 다른 키")
	void differs() {
		LoginEmailKey a = keyWith(SECRET_A);
		assertThat(a.of("one@example.com")).isNotEqualTo(a.of("two@example.com"));
		assertThat(a.of("one@example.com")).isNotEqualTo(keyWith(SECRET_B).of("one@example.com"));
	}

	@Test
	@DisplayName("비밀키가 없거나 32바이트 미만이면 기동 실패")
	void requiresSecret() {
		assertThatThrownBy(() -> new AuthProperties.Login(5, Duration.ofMinutes(10), null))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new AuthProperties.Login(5, Duration.ofMinutes(10), "short"))
				.isInstanceOf(IllegalStateException.class);
	}
}
