package com.aibeauty.api.global.ratelimit;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.global.error.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증 API의 IP 기준 요청 횟수 제한 (FIX-009).
 * 받는 이메일 기준 제한(MAIL_EMAIL)은 요청 본문의 이메일이 필요해 각 서비스에서 건다.
 *
 * 보안 필터 체인 안에서 CSRF 헤더 검사 다음에 둔다 (SecurityConfig).
 * 빈으로 등록하지 않는다 — 등록하면 서블릿 필터로도 한 번 더 걸려 두 번 센다.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	/** POST 경로 → 적용할 제한 */
	private static final Map<String, RateLimitRule> RULES = Map.of(
			"/api/auth/login", RateLimitRule.LOGIN_IP,
			"/api/auth/signup", RateLimitRule.AUTH_IP,
			"/api/auth/email-verification/confirm", RateLimitRule.AUTH_IP,
			"/api/auth/password-reset/confirm", RateLimitRule.AUTH_IP,
			"/api/auth/email-verification/request", RateLimitRule.MAIL_IP,
			"/api/auth/password-reset/request", RateLimitRule.MAIL_IP);

	private final RateLimitService rateLimitService;
	private final ClientIpResolver clientIpResolver;
	private final ErrorResponseWriter errorResponseWriter;

	public RateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver,
			ErrorResponseWriter errorResponseWriter) {
		this.rateLimitService = rateLimitService;
		this.clientIpResolver = clientIpResolver;
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		RateLimitRule rule = RULES.get(request.getRequestURI());
		if (rule != null) {
			try {
				rateLimitService.check(rule, clientIpResolver.resolve(request));
			}
			catch (RateLimitExceededException e) {
				response.setHeader("Retry-After", String.valueOf(e.retryAfterSeconds()));
				errorResponseWriter.write(response, CommonErrorCode.TOO_MANY_REQUESTS);
				return;
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !RULES.containsKey(request.getRequestURI());
	}
}
