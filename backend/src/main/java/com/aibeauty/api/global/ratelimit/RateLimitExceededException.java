package com.aibeauty.api.global.ratelimit;

import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.CommonErrorCode;

/** 요청 횟수 제한 초과 (429). 응답에 Retry-After(초) 헤더를 붙인다 (FIX-009). */
public class RateLimitExceededException extends BusinessException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super(CommonErrorCode.TOO_MANY_REQUESTS, "retryAfter=" + retryAfterSeconds + "s");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
