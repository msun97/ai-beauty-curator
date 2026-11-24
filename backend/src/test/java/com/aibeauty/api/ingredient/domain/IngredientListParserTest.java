package com.aibeauty.api.ingredient.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientListParserTest {

	@Test
	@DisplayName("쉼표로 나누되 숫자 사이와 괄호 안의 쉼표는 나누지 않는다")
	void splitsRespectingDigitsAndParentheses() {
		assertThat(IngredientListParser.parse("정제수, 1,2-헥산다이올, 향료(리모넨, 리날룰), 글리세린"))
				.containsExactly("정제수", "1,2-헥산다이올", "향료(리모넨, 리날룰)", "글리세린");
	}

	@Test
	@DisplayName("끝의 마침표와 주석 표시를 지우고 빈 항목은 뺀다")
	void cleansMarks() {
		assertThat(IngredientListParser.parse("*병풀추출물, 정제수,, 다이소듐이디티에이."))
				.containsExactly("병풀추출물", "정제수", "다이소듐이디티에이");
		assertThat(IngredientListParser.parse("  ")).isEmpty();
	}

	@Test
	@DisplayName("원문 표기와 순서를 그대로 유지한다")
	void keepsOriginalSpellingAndOrder() {
		assertThat(IngredientListParser.parse("부틸렌글리콜, 정제수"))
				.containsExactly("부틸렌글리콜", "정제수");
	}
}
