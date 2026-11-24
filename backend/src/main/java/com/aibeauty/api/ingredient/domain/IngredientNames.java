package com.aibeauty.api.ingredient.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 성분명 표기 차이를 흡수하는 규칙.
 * 상품 전성분 매칭과 별칭 중복 판단이 같은 규칙을 써야 하므로 한곳에 둔다.
 */
public final class IngredientNames {

	/** 숫자 사이의 쉼표(예: 1,2-헥산다이올)는 이름의 일부이고, 그 밖의 쉼표는 이름 구분자다. */
	private static final Pattern NAME_SEPARATOR = Pattern.compile("(?<!\\d),|,(?!\\d)");
	private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

	private IngredientNames() {
	}

	/**
	 * 비교용 값. 전각·반각을 통일하고(NFKC) 소문자로 바꾼 뒤 글자와 숫자만 남긴다.
	 * 예: "Niacinamide (B3)" → "niacinamideb3", "나이아신 아마이드" → "나이아신아마이드"
	 */
	public static String normalize(String name) {
		if (name == null) {
			return "";
		}
		String folded = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase();
		return NOT_LETTER_OR_DIGIT.matcher(folded).replaceAll("");
	}

	/** 한 필드에 쉼표로 묶여 온 여러 이름을 나눈다. 앞뒤 공백·제어문자는 지우고 빈 값과 중복은 뺀다. */
	public static List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (String part : NAME_SEPARATOR.split(value)) {
			String cleaned = clean(part);
			if (!cleaned.isEmpty()) {
				names.add(cleaned);
			}
		}
		return new ArrayList<>(names);
	}

	/** 제어문자를 공백으로 바꾸고 연속 공백을 하나로 줄인다. */
	public static String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
	}
}
