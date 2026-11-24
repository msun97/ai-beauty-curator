package com.aibeauty.api.ingredient.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientNamesTest {

	@Test
	@DisplayName("비교용 값은 띄어쓰기·기호·대소문자·전각 차이를 없앤다")
	void normalize() {
		assertThat(IngredientNames.normalize("나이아신 아마이드")).isEqualTo("나이아신아마이드");
		assertThat(IngredientNames.normalize("Niacinamide (B3)")).isEqualTo("niacinamideb3");
		assertThat(IngredientNames.normalize("１,２-헥산다이올")).isEqualTo("12헥산다이올");
		assertThat(IngredientNames.normalize("  - ")).isEmpty();
	}

	@Test
	@DisplayName("쉼표로 묶인 이름을 나누되 숫자 사이 쉼표는 이름의 일부로 둔다")
	void split() {
		assertThat(IngredientNames.split("리치열매추출물,여지열매추출물")).containsExactly("리치열매추출물", "여지열매추출물");
		assertThat(IngredientNames.split("Riboflavin, Lactoflavin")).containsExactly("Riboflavin", "Lactoflavin");
		assertThat(IngredientNames.split("1,2-Hexanediol")).containsExactly("1,2-Hexanediol");
		assertThat(IngredientNames.split("잎/줄기추출물")).containsExactly("잎/줄기추출물");
	}

	@Test
	@DisplayName("제어문자·중복·빈 값은 정리한다")
	void splitCleansNoise() {
		assertThat(IngredientNames.split("가지추출물\r,가지추출물, ,\t")).containsExactly("가지추출물");
		assertThat(IngredientNames.split(null)).isEmpty();
	}
}
