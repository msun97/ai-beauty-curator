package com.aibeauty.api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private static ClientIpResolver resolver(int trustedProxies) {
		RateLimitProperties.Rule rule = new RateLimitProperties.Rule(10, Duration.ofMinutes(1));
		return new ClientIpResolver(new RateLimitProperties(true, trustedProxies,
				Map.of("login-ip", rule, "auth-ip", rule, "mail-ip", rule, "mail-email", rule)));
	}

	private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		for (String value : forwardedFor) {
			request.addHeader("X-Forwarded-For", value);
		}
		return request;
	}

	@Test
	@DisplayName("프록시 0개(로컬): X-Forwarded-For를 무시하고 접속 주소를 쓴다 (꾸민 헤더로 우회 불가)")
	void noProxy() {
		assertThat(resolver(0).resolve(request("203.0.113.9", "1.1.1.1"))).isEqualTo("203.0.113.9");
	}

	@Test
	@DisplayName("프록시 1개: 헤더의 마지막 값(우리 프록시가 받은 상대)을 쓰고, 앞에 꾸며 넣은 값은 무시한다")
	void oneProxy() {
		// 공격자가 "1.1.1.1"을 넣어 보냈고, 우리 프록시가 실제 주소 198.51.100.7을 뒤에 붙였다
		assertThat(resolver(1).resolve(request("10.0.0.2", "1.1.1.1, 198.51.100.7"))).isEqualTo("198.51.100.7");
		assertThat(resolver(1).resolve(request("10.0.0.2", "198.51.100.7"))).isEqualTo("198.51.100.7");
	}

	@Test
	@DisplayName("프록시 2개: 뒤에서 두 번째 값, 헤더가 여러 줄이어도 순서대로 합친다")
	void twoProxies() {
		assertThat(resolver(2).resolve(request("10.0.0.3", "1.1.1.1, 198.51.100.7", "10.0.0.2")))
			.isEqualTo("198.51.100.7");
	}

	@Test
	@DisplayName("헤더 값이 프록시 수보다 적으면(프록시를 거치지 않음) 접속 주소를 쓴다")
	void notThroughProxy() {
		assertThat(resolver(2).resolve(request("203.0.113.9", "198.51.100.7"))).isEqualTo("203.0.113.9");
		assertThat(resolver(1).resolve(request("203.0.113.9"))).isEqualTo("203.0.113.9");
	}
}
