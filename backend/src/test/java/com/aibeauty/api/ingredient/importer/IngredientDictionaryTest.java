package com.aibeauty.api.ingredient.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngredientDictionaryTest {

	private static MfdsIngredientItem item(String name, String english, String synonyms) {
		return new MfdsIngredientItem(name, english, null, null, synonyms);
	}

	@Test
	@DisplayName("표준명·영문명·이명을 모두 별칭으로 만들고, 첫 영문명을 INCI명으로 쓴다")
	void buildsEntriesAndAliases() {
		IngredientDictionary dictionary = IngredientDictionary.from(List.of(
				new MfdsIngredientItem("리보플라빈", "Riboflavin,Lactoflavin", "83-88-5", "비타민 B2이다.", "비타민비2")));

		assertThat(dictionary.entries())
				.extracting(IngredientDictionary.Entry::standardName, IngredientDictionary.Entry::inciName,
						IngredientDictionary.Entry::description)
				.containsExactly(tuple("리보플라빈", "Riboflavin", "비타민 B2이다."));
		assertThat(dictionary.aliases())
				.extracting(IngredientDictionary.Alias::alias, IngredientDictionary.Alias::normalizedAlias)
				.containsExactly(
						tuple("리보플라빈", "리보플라빈"),
						tuple("Riboflavin", "riboflavin"),
						tuple("Lactoflavin", "lactoflavin"),
						tuple("비타민비2", "비타민비2"));
	}

	@Test
	@DisplayName("다른 성분의 이명과 겹치면 표준명 쪽을 택한다")
	void standardNameWinsOverSynonym() {
		IngredientDictionary dictionary = IngredientDictionary.from(List.of(
				item("가지추출물", null, null),
				item("가지열매추출물", "Solanum Melongena Fruit Extract", "가지 추출물")));

		assertThat(dictionary.aliases())
				.filteredOn(alias -> alias.normalizedAlias().equals("가지추출물"))
				.extracting(IngredientDictionary.Alias::standardName)
				.containsExactly("가지추출물");
		assertThat(dictionary.ambiguousAliases()).isEmpty();
	}

	@Test
	@DisplayName("같은 우선순위에서 여러 성분이 겹치는 별칭은 어디에도 등록하지 않는다")
	void ambiguousAliasIsDropped() {
		IngredientDictionary dictionary = IngredientDictionary.from(List.of(
				item("백단향목부추출물", null, "백단향추출물"),
				item("인도백단향추출물", null, "백단향 추출물")));

		assertThat(dictionary.aliases()).extracting(IngredientDictionary.Alias::normalizedAlias)
				.doesNotContain("백단향추출물");
		assertThat(dictionary.ambiguousAliases()).singleElement().satisfies(a -> {
			assertThat(a.normalizedAlias()).isEqualTo("백단향추출물");
			assertThat(a.standardNames()).containsExactly("백단향목부추출물", "인도백단향추출물");
		});
	}

	@Test
	@DisplayName("원천에 같은 표준명이 반복되면 하나로 합치고, 비어 있는 이름은 건너뛴다")
	void mergesDuplicatesAndSkipsBlank() {
		IngredientDictionary dictionary = IngredientDictionary.from(List.of(
				new MfdsIngredientItem("정제수", null, null, null, null),
				new MfdsIngredientItem(" 정제수 ", "Water", null, "물이다.", "물"),
				item("  ", "Unknown", null)));

		assertThat(dictionary.entries()).singleElement().satisfies(entry -> {
			assertThat(entry.standardName()).isEqualTo("정제수");
			assertThat(entry.inciName()).isEqualTo("Water");
			assertThat(entry.description()).isEqualTo("물이다.");
		});
		assertThat(dictionary.skippedItems()).isEqualTo(1);
		assertThat(dictionary.aliases()).extracting(IngredientDictionary.Alias::normalizedAlias)
				.containsExactly("정제수", "water", "물");
	}
}
