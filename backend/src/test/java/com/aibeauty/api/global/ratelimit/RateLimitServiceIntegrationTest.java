package com.aibeauty.api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.aibeauty.api.TestcontainersConfiguration;

/** 요청 횟수 제한 카운터 (FIX-009). 이 테스트에서는 login-ip를 1분 3회로 낮춘다 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "app.rate-limit.rules.login-ip.limit=3", "app.rate-limit.rules.login-ip.window=60s" })
class RateLimitServiceIntegrationTest {

	@Autowired
	RateLimitService service;

	@Autowired
	JdbcTemplate jdbc;

	private static String newSubject() {
		return "10.0." + UUID.randomUUID();
	}

	@Test
	@DisplayName("한도(3회)까지는 통과하고 4번째부터 429, Retry-After는 1~60초")
	void limit() {
		String ip = newSubject();
		for (int i = 0; i < 3; i++) {
			assertThatCode(() -> service.check(RateLimitRule.LOGIN_IP, ip)).doesNotThrowAnyException();
		}
		assertThatThrownBy(() -> service.check(RateLimitRule.LOGIN_IP, ip))
			.isInstanceOf(RateLimitExceededException.class)
			.satisfies(e -> assertThat(((RateLimitExceededException) e).retryAfterSeconds()).isBetween(1L, 60L))
			.satisfies(e -> assertThat(((RateLimitExceededException) e).errorCode().name()).isEqualTo("TOO_MANY_REQUESTS"));
	}

	@Test
	@DisplayName("대상(IP)과 제한 종류가 다르면 따로 센다")
	void separateCounters() {
		String a = newSubject();
		String b = newSubject();
		for (int i = 0; i < 3; i++) service.check(RateLimitRule.LOGIN_IP, a);
		assertThatCode(() -> service.check(RateLimitRule.LOGIN_IP, b)).doesNotThrowAnyException();
		assertThatCode(() -> service.check(RateLimitRule.AUTH_IP, a)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("시간 구간이 바뀌면 처음부터 다시 센다")
	void newWindowResets() {
		String ip = newSubject();
		for (int i = 0; i < 3; i++) service.check(RateLimitRule.LOGIN_IP, ip);
		// 저장된 구간을 1분 전으로 옮겨, 지금 요청이 새 구간이 되게 한다
		jdbc.update("UPDATE rate_limit_counters SET window_start = window_start - interval '60 seconds'");
		assertThatCode(() -> service.check(RateLimitRule.LOGIN_IP, ip)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("키에 IP·이메일 원문이 남지 않는다")
	void noRawSubject() {
		String email = "rate-" + UUID.randomUUID() + "@example.com";
		service.check(RateLimitRule.MAIL_EMAIL, email);
		Integer raw = jdbc.queryForObject("SELECT count(*) FROM rate_limit_counters WHERE counter_key LIKE ?",
				Integer.class, "%" + email.substring(0, 12) + "%");
		assertThat(raw).isZero();
	}

	@Test
	@DisplayName("동시에 10번 요청해도 정확히 3번만 통과한다")
	void concurrent() throws Exception {
		String ip = newSubject();
		ExecutorService pool = Executors.newFixedThreadPool(10);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try {
			for (int i = 0; i < 10; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						service.check(RateLimitRule.LOGIN_IP, ip);
						return true;
					}
					catch (RateLimitExceededException e) {
						return false;
					}
				}));
			}
			start.countDown();
			long passed = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) passed++;
			}
			assertThat(passed).isEqualTo(3);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("정리: 구간이 끝난 카운터만 지운다")
	void deleteExpired() {
		String ended = newSubject();
		String active = newSubject();
		service.check(RateLimitRule.LOGIN_IP, ended);
		jdbc.update("UPDATE rate_limit_counters SET expires_at = now() - interval '1 second' "
				+ "WHERE expires_at > now() AND counter_key IN (SELECT counter_key FROM rate_limit_counters "
				+ "ORDER BY window_start DESC LIMIT 1)");
		service.check(RateLimitRule.AUTH_IP, active);
		int before = jdbc.queryForObject("SELECT count(*) FROM rate_limit_counters WHERE expires_at > now()", Integer.class);

		service.deleteExpired(Instant.now());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_counters WHERE expires_at <= now()", Integer.class))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_counters WHERE expires_at > now()", Integer.class))
			.isEqualTo(before);
	}
}
