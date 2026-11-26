package com.aibeauty.api.global.security;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 토큰을 HttpOnly 쿠키로 내려준다. 브라우저는 Next.js 도메인으로만 요청하고 Next.js가 /api/** 를
 * 백엔드로 전달하므로, 쿠키는 프론트엔드 도메인의 first-party 쿠키가 된다 (ADR-004).
 */
@Component
public class AuthCookies {

	public static final String ACCESS_TOKEN = "access_token";
	public static final String REFRESH_TOKEN = "refresh_token";

	/**
	 * Refresh Token도 모든 경로로 보낸다. 액세스 토큰(15분)이 만료된 뒤 화면을 이동할 때
	 * Next.js 서버(proxy)가 이 쿠키로 대신 재발급할 수 있어야 하기 때문이다 (FIX-002, FIX-004).
	 * HttpOnly라 스크립트로 읽을 수 없고, 재발급 API는 CSRF 헤더 검사와 SameSite=Lax로 보호된다.
	 */
	private static final String REFRESH_TOKEN_PATH = "/";

	/**
	 * 예전 Refresh Token 경로. 브라우저는 이름이 같아도 경로가 다르면 다른 쿠키로 보관하고,
	 * 요청에는 경로가 더 구체적인 쿠키를 먼저 보낸다. 예전 쿠키가 남아 있으면 이미 쓴 토큰이 먼저 전송되어
	 * 재사용(탈취)으로 판단되므로, 쿠키를 쓰거나 지울 때마다 함께 삭제한다.
	 */
	private static final String LEGACY_REFRESH_TOKEN_PATH = "/api/auth";

	private final AuthProperties properties;

	public AuthCookies(AuthProperties properties) {
		this.properties = properties;
	}

	public void write(HttpServletResponse response, String accessToken, String refreshToken) {
		add(response, ACCESS_TOKEN, accessToken, "/", properties.jwt().accessTokenTtl());
		add(response, REFRESH_TOKEN, refreshToken, REFRESH_TOKEN_PATH, properties.jwt().refreshTokenTtl());
		expireLegacyRefreshToken(response);
	}

	public void clear(HttpServletResponse response) {
		add(response, ACCESS_TOKEN, "", "/", Duration.ZERO);
		add(response, REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO);
		expireLegacyRefreshToken(response);
	}

	private void expireLegacyRefreshToken(HttpServletResponse response) {
		add(response, REFRESH_TOKEN, "", LEGACY_REFRESH_TOKEN_PATH, Duration.ZERO);
	}

	private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
		ResponseCookie cookie = ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(properties.cookie().secure())
			.sameSite("Lax")
			.path(path)
			.maxAge(maxAge)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
