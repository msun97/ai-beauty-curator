package com.aibeauty.api.ingredient.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientMatcherTest {

	private final IngredientMatcher matcher = new IngredientMatcher(
			Map.of("정제수", 1L, "1,2-헥산다이올", 2L, "향료", 3L),
			Map.of("정제수", 1L, "water", 1L, "12헥산다이올", 2L, "12헥산디올", 2L, "향료", 3L));

	@Test
	@DisplayName("표준명, 별칭, 띄어쓰기·대소문자 차이로 매칭한다")
	void matches() {
		assertThat(matcher.match("정제수")).contains(1L);
		assertThat(matcher.match("Water")).contains(1L);
		assertThat(matcher.match("1,2-헥산 디올")).contains(2L);
	}

	@Test
	@DisplayName("괄호 부분을 빼고 다시 찾는다")
	void retriesWithoutParentheses() {
		assertThat(matcher.match("정제수(Water)")).contains(1L);
		assertThat(matcher.match("향료(리모넨, 리날룰)")).contains(3L);
	}

	@Test
	@DisplayName("비슷하기만 한 이름은 매칭하지 않는다")
	void doesNotGuess() {
		assertThat(matcher.match("정제수추출물")).isEmpty();
		assertThat(matcher.match("헥산다이올")).isEmpty();
	}
}
