package com.aibeauty.api.global.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.global.error.ErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 쿠키 인증을 쓰므로 CSRF를 막기 위해, 상태를 바꾸는 요청에는 커스텀 헤더를 요구한다.
 * 다른 사이트의 form 전송은 커스텀 헤더를 붙일 수 없고, fetch로 붙이면 CORS 사전 요청에서 차단된다.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Requested-With";

	private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

	private final ErrorResponseWriter errorResponseWriter;

	public CsrfHeaderFilter(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HEADER);
		if (UNSAFE_METHODS.contains(request.getMethod()) && (header == null || header.isBlank())) {
			errorResponseWriter.write(response, CommonErrorCode.CSRF_HEADER_MISSING);
			return;
		}
		chain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}
}
