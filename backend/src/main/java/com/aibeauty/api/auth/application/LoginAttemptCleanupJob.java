package com.aibeauty.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 인증 기록 정리. 가입하지 않은 이메일의 기록도 쌓이므로 필요 이상 보관하지 않는다.
 * - 로그인 실패 기록(FIX-006): 잠금이 끝났고(또는 잠긴 적 없고) 마지막 시도가 하루 넘게 지난 행
 * - 가입 이메일 인증(FIX-008): 코드·토큰이 모두 만료됐거나 토큰을 썼고 마지막 변경이 하루 넘게 지난 행
 * 개인정보 처리방침의 보관 기간(하루)과 같아야 한다.
 */
@Component
public class LoginAttemptCleanupJob {

	/** 마지막 시도 후 이만큼 지나면 지운다 */
	static final Duration RETENTION = Duration.ofDays(1);

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptCleanupJob.class);

	private final LoginAttemptService loginAttemptService;
	private final EmailVerificationService emailVerificationService;
	private final Clock clock;

	public LoginAttemptCleanupJob(LoginAttemptService loginAttemptService,
			EmailVerificationService emailVerificationService, Clock clock) {
		this.loginAttemptService = loginAttemptService;
		this.emailVerificationService = emailVerificationService;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.auth.login.cleanup-cron:0 0 * * * *}")
	public void deleteStale() {
		Instant now = clock.instant();
		Instant staleBefore = now.minus(RETENTION);
		int attempts = loginAttemptService.deleteStale(staleBefore, now);
		int verifications = emailVerificationService.deleteStale(staleBefore, now);
		if (attempts > 0 || verifications > 0) {
			log.info("오래된 인증 기록 삭제: 로그인 실패 {}건, 이메일 인증 {}건", attempts, verifications);
		}
	}
}
