package com.aibeauty.api.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

	private static final Instant USED_AT = Instant.parse("2026-11-26T08:00:00Z");
	private static final Duration GRACE = Duration.ofSeconds(10);

	private static RefreshToken usedToken() {
		RefreshToken token = RefreshToken.issue(null, "hash", UUID.randomUUID(), USED_AT.plus(Duration.ofDays(14)),
				USED_AT.minusSeconds(60));
		token.markUsed(USED_AT);
		return token;
	}

	@Test
	@DisplayName("사용된 지 유예 시간 이내면 동시 요청으로 본다 (경계값 10초 포함)")
	void withinGrace() {
		RefreshToken token = usedToken();
		assertThat(token.isUsedWithin(GRACE, USED_AT)).isTrue();
		assertThat(token.isUsedWithin(GRACE, USED_AT.plusSeconds(3))).isTrue();
		assertThat(token.isUsedWithin(GRACE, USED_AT.plusSeconds(10))).isTrue();
	}

	@Test
	@DisplayName("유예 시간이 지나면 동시 요청으로 보지 않는다")
	void afterGrace() {
		assertThat(usedToken().isUsedWithin(GRACE, USED_AT.plusSeconds(10).plusMillis(1))).isFalse();
	}

	@Test
	@DisplayName("유예 시간이 0이면 같은 시각이라도 동시 요청으로 보지 않는다 (가장 엄격)")
	void zeroGrace() {
		assertThat(usedToken().isUsedWithin(Duration.ZERO, USED_AT)).isFalse();
	}

	@Test
	@DisplayName("아직 사용하지 않은 토큰은 해당 없음")
	void notUsed() {
		RefreshToken token = RefreshToken.issue(null, "hash", UUID.randomUUID(), USED_AT.plus(Duration.ofDays(14)), USED_AT);
		assertThat(token.isUsedWithin(GRACE, USED_AT)).isFalse();
	}
}
