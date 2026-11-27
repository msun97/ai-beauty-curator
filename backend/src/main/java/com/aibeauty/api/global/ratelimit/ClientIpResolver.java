package com.aibeauty.api.global.ratelimit;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 사람의 IP를 알아낸다 (FIX-009).
 *
 * 배포하면 요청이 프록시(Vercel의 Next.js 서버, Render의 로드밸런서 등)를 거쳐 오므로
 * 백엔드가 보는 접속 주소(remoteAddr)는 프록시의 주소다. 진짜 IP는 X-Forwarded-For 헤더에 쌓인다:
 *   X-Forwarded-For: <요청한 사람>, <프록시1>, <프록시2>  (각 프록시가 받은 상대의 주소를 뒤에 붙임)
 *
 * 이 헤더는 요청한 사람이 아무 값이나 넣어 보낼 수 있다. 그래서 앞에서부터 읽지 않고,
 * 믿을 수 있는 프록시 수(trustedProxies)만큼 뒤에서부터 세어 "우리 프록시가 직접 받은 상대"의 주소를 쓴다.
 * - trustedProxies = 0: 헤더를 무시하고 접속 주소를 쓴다 (로컬, 프록시 없음)
 * - trustedProxies = N: 헤더의 뒤에서 N번째 값
 * 헤더 값이 N개보다 적으면(프록시를 거치지 않은 요청) 접속 주소를 쓴다.
 */
@Component
public class ClientIpResolver {

	static final String FORWARDED_FOR = "X-Forwarded-For";

	private final int trustedProxies;

	public ClientIpResolver(RateLimitProperties properties) {
		this.trustedProxies = properties.trustedProxies();
	}

	public String resolve(HttpServletRequest request) {
		if (trustedProxies == 0) {
			return request.getRemoteAddr();
		}
		List<String> hops = forwardedFor(request);
		if (hops.size() < trustedProxies) {
			return request.getRemoteAddr();
		}
		return hops.get(hops.size() - trustedProxies);
	}

	/** 여러 개의 X-Forwarded-For 헤더를 순서대로 합친 주소 목록 */
	private static List<String> forwardedFor(HttpServletRequest request) {
		return java.util.Collections.list(request.getHeaders(FORWARDED_FOR)).stream()
			.flatMap(value -> Arrays.stream(value.split(",")))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.toList();
	}
}
