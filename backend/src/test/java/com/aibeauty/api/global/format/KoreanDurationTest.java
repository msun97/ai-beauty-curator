package com.aibeauty.api.global.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanDurationTest {

	@Test
	@DisplayName("한 단위로 떨어지는 기간")
	void singleUnit() {
		assertThat(KoreanDuration.format(Duration.ofMinutes(10))).isEqualTo("10분");
		assertThat(KoreanDuration.format(Duration.ofHours(1))).isEqualTo("1시간");
		assertThat(KoreanDuration.format(Duration.ofSeconds(30))).isEqualTo("30초");
		assertThat(KoreanDuration.format(Duration.ofDays(1))).isEqualTo("1일");
	}

	@Test
	@DisplayName("여러 단위가 섞인 기간은 큰 단위부터, 0인 단위는 생략")
	void mixedUnits() {
		assertThat(KoreanDuration.format(Duration.ofSeconds(90))).isEqualTo("1분 30초");
		assertThat(KoreanDuration.format(Duration.ofMinutes(90))).isEqualTo("1시간 30분");
		assertThat(KoreanDuration.format(Duration.ofHours(1).plusSeconds(5))).isEqualTo("1시간 5초");
	}

	@Test
	@DisplayName("0초와 잘못된 값")
	void edgeCases() {
		assertThat(KoreanDuration.format(Duration.ZERO)).isEqualTo("0초");
		assertThatThrownBy(() -> KoreanDuration.format(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> KoreanDuration.format(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
