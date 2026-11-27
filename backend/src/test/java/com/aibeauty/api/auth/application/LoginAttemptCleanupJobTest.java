package com.aibeauty.api.auth.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aibeauty.api.global.ratelimit.RateLimitService;

class LoginAttemptCleanupJobTest {

	@Test
	@DisplayName("로그인 실패·이메일 인증 기록은 하루 지난 것, 요청 횟수 카운터는 구간이 끝난 것을 지운다")
	void deletesStaleRecords() {
		Instant now = Instant.parse("2026-11-27T03:00:00Z");
		LoginAttemptService service = mock(LoginAttemptService.class);
		EmailVerificationService verifications = mock(EmailVerificationService.class);
		RateLimitService counters = mock(RateLimitService.class);

		new LoginAttemptCleanupJob(service, verifications, counters, Clock.fixed(now, ZoneOffset.UTC)).deleteStale();

		verify(service).deleteStale(now.minus(Duration.ofDays(1)), now);
		verify(verifications).deleteStale(now.minus(Duration.ofDays(1)), now);
		verify(counters).deleteExpired(now);
	}
}
