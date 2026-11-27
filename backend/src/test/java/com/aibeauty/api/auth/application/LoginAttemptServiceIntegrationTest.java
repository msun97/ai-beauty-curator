package com.aibeauty.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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

import com.aibeauty.api.TestcontainersConfiguration;

/** 이메일 기준 로그인 실패 기록 (FIX-006). 설정 기본값: 5번 실패 시 10분 잠금 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LoginAttemptServiceIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");

	@Autowired
	LoginAttemptService service;

	@Autowired
	JdbcTemplate jdbc;

	private static String newKey() {
		return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
	}

	@Test
	@DisplayName("4번째까지는 잠기지 않고 5번째 실패에서 10분 잠긴다")
	void locksOnFifthFailure() {
		String key = newKey();
		for (int i = 1; i <= 4; i++) {
			assertThat(service.recordFailure(key, NOW)).as("%d번째", i).isFalse();
		}
		assertThat(service.isLocked(key, NOW)).isFalse();

		assertThat(service.recordFailure(key, NOW)).isTrue();
		assertThat(service.isLocked(key, NOW)).isTrue();
		assertThat(service.isLocked(key, NOW.plus(Duration.ofMinutes(10)).minusSeconds(1))).isTrue();
		assertThat(service.isLocked(key, NOW.plus(Duration.ofMinutes(10)))).isFalse();
	}

	@Test
	@DisplayName("잠금이 끝나면 다시 처음부터 5번을 센다")
	void countsAgainAfterLock() {
		String key = newKey();
		for (int i = 0; i < 5; i++) service.recordFailure(key, NOW);
		Instant later = NOW.plus(Duration.ofMinutes(11));
		for (int i = 1; i <= 4; i++) {
			assertThat(service.recordFailure(key, later)).as("잠금 후 %d번째", i).isFalse();
		}
		assertThat(service.recordFailure(key, later)).isTrue();
	}

	@Test
	@DisplayName("기록이 없는 키는 잠기지 않았고, reset하면 기록과 잠금이 사라진다")
	void reset() {
		String key = newKey();
		assertThat(service.isLocked(key, NOW)).isFalse();
		for (int i = 0; i < 5; i++) service.recordFailure(key, NOW);
		service.reset(key);
		assertThat(service.isLocked(key, NOW)).isFalse();
		assertThat(count(key)).isZero();
	}

	@Test
	@DisplayName("같은 키로 동시에 5번 실패해도 횟수가 빠지지 않아 정확히 한 번 잠긴다")
	void concurrentFailures() throws Exception {
		String key = newKey();
		int threads = 5;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				Callable<Boolean> task = () -> {
					start.await();
					return service.recordFailure(key, NOW);
				};
				results.add(pool.submit(task));
			}
			start.countDown();
			long locked = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) locked++;
			}
			assertThat(locked).as("잠금을 일으킨 요청 수").isEqualTo(1);
			assertThat(service.isLocked(key, NOW)).isTrue();
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("정리: 잠금이 끝났고 오래된 행만 지우고, 아직 잠긴 행과 최근 행은 남긴다")
	void deleteStale() {
		String old = newKey();
		String oldButLocked = newKey();
		String recent = newKey();
		Instant twoDaysAgo = NOW.minus(Duration.ofDays(2));
		service.recordFailure(old, twoDaysAgo);
		jdbc.update("INSERT INTO login_attempts (email_key, failed_count, locked_until, updated_at) VALUES (?, 0, ?, ?)",
				oldButLocked, java.sql.Timestamp.from(NOW.plus(Duration.ofMinutes(5))), java.sql.Timestamp.from(twoDaysAgo));
		service.recordFailure(recent, NOW);

		service.deleteStale(NOW.minus(Duration.ofDays(1)), NOW);

		assertThat(count(old)).isZero();
		assertThat(count(oldButLocked)).isOne();
		assertThat(count(recent)).isOne();
	}

	private int count(String key) {
		return jdbc.queryForObject("SELECT count(*) FROM login_attempts WHERE email_key = ?", Integer.class, key);
	}
}
