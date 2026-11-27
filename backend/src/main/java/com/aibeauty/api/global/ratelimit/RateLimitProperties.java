package com.aibeauty.api.global.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청 횟수 제한 설정 (FIX-009, app.rate-limit).
 *
 * enabled: 끄면 모든 제한을 건너뛴다 (장애 시 비상용).
 * trustedProxies: 백엔드 앞에 있는 믿을 수 있는 프록시 수. 요청한 사람의 IP를 X-Forwarded-For에서 고를 때 쓴다.
 *   로컬은 0 (헤더를 믿지 않음). 배포 구조(Vercel, Render 등)에 맞춰 8주차에 정한다.
 * rules: 제한 종류별 한도(limit)와 시간 구간(window). 모든 RateLimitRule이 있어야 한다.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, int trustedProxies, Map<String, Rule> rules) {

	public RateLimitProperties {
		if (trustedProxies < 0) {
			throw new IllegalStateException("app.rate-limit.trusted-proxies는 0 이상이어야 합니다.");
		}
		rules = rules == null ? Map.of() : Map.copyOf(rules);
		for (RateLimitRule rule : RateLimitRule.values()) {
			Rule value = rules.get(rule.propertyName());
			if (value == null || value.limit() < 1 || value.window() == null || value.window().toSeconds() < 1) {
				throw new IllegalStateException(
						"app.rate-limit.rules." + rule.propertyName() + " (limit 1 이상, window 1초 이상)이 필요합니다.");
			}
		}
	}

	public Rule rule(RateLimitRule rule) {
		return rules.get(rule.propertyName());
	}

	public record Rule(int limit, Duration window) {
	}
}
