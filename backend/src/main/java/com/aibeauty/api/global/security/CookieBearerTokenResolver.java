package com.aibeauty.api.global.security;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Access Token을 Authorization 헤더(Next.js 서버 컴포넌트 호출) 또는 쿠키(브라우저 호출)에서 꺼낸다.
 *
 * <p>/api/auth/** 에서는 토큰을 읽지 않는다. 만료된 Access Token 쿠키가 남아 있어도
 * 로그인·토큰 재발급 요청이 401로 막히지 않게 하기 위해서다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

	private static final String AUTH_PATH_PREFIX = "/api/auth/";

	private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

	@Override
	public String resolve(HttpServletRequest request) {
		if (request.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
			return null;
		}
		String fromHeader = headerResolver.resolve(request);
		if (fromHeader != null) {
			return fromHeader;
		}
		Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
		return (cookie == null || cookie.getValue().isBlank()) ? null : cookie.getValue();
	}
}
