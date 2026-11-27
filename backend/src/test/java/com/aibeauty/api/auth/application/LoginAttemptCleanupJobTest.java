package com.aibeauty.api.auth.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginAttemptCleanupJobTest {

	@Test
	@DisplayName("로그인 실패 기록과 이메일 인증 기록 모두 하루 넘게 지난 것을 지우도록 기준 시각을 넘긴다")
	void deletesOlderThanOneDay() {
		Instant now = Instant.parse("2026-11-27T03:00:00Z");
		LoginAttemptService service = mock(LoginAttemptService.class);
		EmailVerificationService verifications = mock(EmailVerificationService.class);

		new LoginAttemptCleanupJob(service, verifications, Clock.fixed(now, ZoneOffset.UTC)).deleteStale();

		verify(service).deleteStale(now.minus(Duration.ofDays(1)), now);
		verify(verifications).deleteStale(now.minus(Duration.ofDays(1)), now);
	}
}
