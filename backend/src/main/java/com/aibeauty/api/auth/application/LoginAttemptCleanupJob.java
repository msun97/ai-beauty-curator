package com.aibeauty.api.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 로그인 실패 기록 정리 (FIX-006).
 *
 * 가입하지 않은 이메일의 기록도 쌓이므로 필요 이상 보관하지 않는다.
 * 잠금이 끝났고(또는 잠긴 적 없고) 마지막 시도가 하루 넘게 지난 행을 지운다.
 * 개인정보 처리방침의 "로그인 실패 기록은 하루 뒤 삭제"와 같아야 한다.
 */
@Component
public class LoginAttemptCleanupJob {

	/** 마지막 시도 후 이만큼 지나면 지운다 */
	static final Duration RETENTION = Duration.ofDays(1);

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptCleanupJob.class);

	private final LoginAttemptService loginAttemptService;
	private final Clock clock;

	public LoginAttemptCleanupJob(LoginAttemptService loginAttemptService, Clock clock) {
		this.loginAttemptService = loginAttemptService;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.auth.login.cleanup-cron:0 0 * * * *}")
	public void deleteStale() {
		Instant now = clock.instant();
		int deleted = loginAttemptService.deleteStale(now.minus(RETENTION), now);
		if (deleted > 0) {
			log.info("오래된 로그인 실패 기록 {}건 삭제", deleted);
		}
	}
}
