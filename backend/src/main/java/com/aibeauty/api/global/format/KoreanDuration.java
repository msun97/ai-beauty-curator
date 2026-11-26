package com.aibeauty.api.global.format;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 기간을 사용자 문구용 한국어로 바꾼다. 예: 10분, 1시간, 1시간 30분, 1분 30초.
 * 설정값(예: 로그인 잠금 시간)을 어떤 단위로 바꿔도 문구가 자연스럽게 읽히게 하기 위해 쓴다.
 */
public final class KoreanDuration {

	private KoreanDuration() {
	}

	public static String format(Duration duration) {
		if (duration == null || duration.isNegative()) {
			throw new IllegalArgumentException("기간은 0 이상이어야 합니다: " + duration);
		}
		long days = duration.toDays();
		int hours = duration.toHoursPart();
		int minutes = duration.toMinutesPart();
		int seconds = duration.toSecondsPart();

		List<String> parts = new ArrayList<>();
		if (days > 0) parts.add(days + "일");
		if (hours > 0) parts.add(hours + "시간");
		if (minutes > 0) parts.add(minutes + "분");
		if (seconds > 0) parts.add(seconds + "초");
		return parts.isEmpty() ? "0초" : String.join(" ", parts);
	}
}
