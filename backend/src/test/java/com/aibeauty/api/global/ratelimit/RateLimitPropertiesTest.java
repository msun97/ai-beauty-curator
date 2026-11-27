package com.aibeauty.api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.aibeauty.api.global.security.AuthProperties;

class RateLimitPropertiesTest {

	private static final RateLimitProperties.Rule RULE = new RateLimitProperties.Rule(10, Duration.ofMinutes(1));

	private static Map<String, RateLimitProperties.Rule> allRules() {
		Map<String, RateLimitProperties.Rule> rules = new HashMap<>();
		for (RateLimitRule rule : RateLimitRule.values()) rules.put(rule.propertyName(), RULE);
		return rules;
	}

	@Test
	@DisplayName("모든 제한 종류의 설정이 있어야 기동한다 (빠뜨리면 기동 실패)")
	void requiresAllRules() {
		assertThatCode(() -> new RateLimitProperties(true, 0, allRules())).doesNotThrowAnyException();

		Map<String, RateLimitProperties.Rule> missing = allRules();
		missing.remove("mail-email");
		assertThatThrownBy(() -> new RateLimitProperties(true, 0, missing)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("mail-email");
	}

	@Test
	@DisplayName("한도 0, 1초 미만 구간, 음수 프록시 수는 기동 실패")
	void rejectsInvalidValues() {
		Map<String, RateLimitProperties.Rule> zeroLimit = allRules();
		zeroLimit.put("login-ip", new RateLimitProperties.Rule(0, Duration.ofMinutes(1)));
		assertThatThrownBy(() -> new RateLimitProperties(true, 0, zeroLimit)).isInstanceOf(IllegalStateException.class);

		Map<String, RateLimitProperties.Rule> tinyWindow = allRules();
		tinyWindow.put("login-ip", new RateLimitProperties.Rule(10, Duration.ofMillis(500)));
		assertThatThrownBy(() -> new RateLimitProperties(true, 0, tinyWindow)).isInstanceOf(IllegalStateException.class);

		assertThatThrownBy(() -> new RateLimitProperties(true, -1, allRules())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("enabled=false면 DB를 건드리지 않고 모두 통과 (장애 시 비상용)")
	void disabled() {
		NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
		AuthProperties auth = new AuthProperties(
				new AuthProperties.Jwt("jwt-secret-0123456789-0123456789-abcdef", "t", Duration.ofMinutes(15),
						Duration.ofDays(14), Duration.ZERO),
				new AuthProperties.Cookie(false),
				new AuthProperties.Login(5, Duration.ofMinutes(10), "login-secret-0123456789-0123456789-abc"),
				new AuthProperties.PasswordReset(Duration.ofMinutes(30), "http://localhost/reset"), "2026-11");
		RateLimitService service = new RateLimitService(jdbc, new RateLimitProperties(false, 0, allRules()), auth,
				Clock.systemUTC());

		for (int i = 0; i < 100; i++) service.check(RateLimitRule.LOGIN_IP, "1.2.3.4");
		verifyNoInteractions(jdbc);
	}
}
