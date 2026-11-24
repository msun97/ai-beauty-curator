package com.aibeauty.api.ingredient.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 전성분 문자열을 표시 순서대로 성분 이름 목록으로 나눈다.
 *
 * <ul>
 * <li>쉼표로 나누되, 괄호 안의 쉼표(예: 향료(리모넨, 리날룰))와 숫자 사이 쉼표(예: 1,2-헥산다이올)는 나누지 않는다.</li>
 * <li>각 이름의 앞뒤 공백과 끝의 마침표, 주석 표시(*)는 지운다. 그 밖의 표기는 원문 그대로 둔다.</li>
 * </ul>
 */
public final class IngredientListParser {

	private IngredientListParser() {
	}

	public static List<String> parse(String ingredients) {
		List<String> names = new ArrayList<>();
		if (ingredients == null || ingredients.isBlank()) {
			return names;
		}
		String text = IngredientNames.clean(ingredients);
		StringBuilder current = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(' || c == '[' || c == '（') {
				depth++;
			} else if ((c == ')' || c == ']' || c == '）') && depth > 0) {
				depth--;
			}
			if (c == ',' && depth == 0 && !betweenDigits(text, i)) {
				add(names, current);
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		add(names, current);
		return names;
	}

	private static boolean betweenDigits(String text, int index) {
		return index > 0 && index < text.length() - 1
				&& Character.isDigit(text.charAt(index - 1)) && Character.isDigit(text.charAt(index + 1));
	}

	private static void add(List<String> names, StringBuilder raw) {
		String name = raw.toString().strip().replaceAll("^\\*+|[*.]+$", "").strip();
		if (!name.isEmpty()) {
			names.add(name);
		}
	}
}
