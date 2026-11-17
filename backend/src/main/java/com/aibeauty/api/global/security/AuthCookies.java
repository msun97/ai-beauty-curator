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

	/** Refresh Token은 재발급·로그아웃 요청에만 전송되도록 경로를 좁힌다. */
	private static final String REFRESH_TOKEN_PATH = "/api/auth";

	private final AuthProperties properties;

	public AuthCookies(AuthProperties properties) {
		this.properties = properties;
	}

	public void write(HttpServletResponse response, String accessToken, String refreshToken) {
		add(response, ACCESS_TOKEN, accessToken, "/", properties.jwt().accessTokenTtl());
		add(response, REFRESH_TOKEN, refreshToken, REFRESH_TOKEN_PATH, properties.jwt().refreshTokenTtl());
	}

	public void clear(HttpServletResponse response) {
		add(response, ACCESS_TOKEN, "", "/", Duration.ZERO);
		add(response, REFRESH_TOKEN, "", REFRESH_TOKEN_PATH, Duration.ZERO);
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
