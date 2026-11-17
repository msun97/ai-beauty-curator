package com.aibeauty.api.global.trace;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 추적 ID를 부여해 로그(MDC)와 응답 헤더, 오류 응답에 함께 남긴다 (NFR-REL-04).
 * 보안 필터보다 먼저 실행되어 인증 실패 로그에도 추적 ID가 남는다 ({@link TraceConfig}).
 *
 * <p>앞단(프록시 등)이 보낸 X-Request-Id가 안전한 형식이면 이어서 쓰고, 아니면 새로 만든다.
 */
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "traceId";

	/** 로그 위조를 막기 위해 영문·숫자·하이픈 64자 이하만 받아들인다. */
	private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String traceId = resolveTraceId(request);
		request.setAttribute(MDC_KEY, traceId);

		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/**
	 * 컨테이너가 오류 페이지(/error)로 다시 디스패치할 때도 실행해, 원래 요청의 추적 ID를 다시 MDC에 넣는다.
	 * (원래 요청의 필터 체인이 끝나면서 MDC가 비워지기 때문)
	 */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	public static String currentTraceId() {
		return MDC.get(MDC_KEY);
	}

	private static String resolveTraceId(HttpServletRequest request) {
		if (request.getAttribute(MDC_KEY) instanceof String existing) {
			return existing;
		}
		String incoming = request.getHeader(HEADER);
		return (incoming != null && SAFE_ID.matcher(incoming).matches()) ? incoming : newTraceId();
	}

	private static String newTraceId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}
}
