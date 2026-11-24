package com.aibeauty.api.ingredient.domain;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 전성분에 표기된 이름을 표준 성분에 연결한다 (FR-ADM-03).
 *
 * <ol>
 * <li>표준명과 정확히 같으면 그 성분</li>
 * <li>비교용 값(띄어쓰기·기호·대소문자 무시)이 별칭과 같으면 그 성분</li>
 * <li>괄호 부분을 뺀 이름으로 1, 2를 다시 시도 (예: "정제수(Water)" → "정제수")</li>
 * <li>그래도 없으면 매칭하지 않는다. 비슷한 이름으로 추측하지 않는다.</li>
 * </ol>
 */
public class IngredientMatcher {

	private static final Pattern PARENTHESIZED = Pattern.compile("\\s*[(\\[（][^)\\]）]*[)\\]）]");

	private final Map<String, Long> idsByStandardName;
	private final Map<String, Long> idsByNormalizedAlias;

	/**
	 * @param idsByStandardName 표준명 → 성분 ID
	 * @param idsByNormalizedAlias 비교용 별칭 → 성분 ID
	 */
	public IngredientMatcher(Map<String, Long> idsByStandardName, Map<String, Long> idsByNormalizedAlias) {
		this.idsByStandardName = Map.copyOf(idsByStandardName);
		this.idsByNormalizedAlias = Map.copyOf(idsByNormalizedAlias);
	}

	public Optional<Long> match(String rawName) {
		String name = IngredientNames.clean(rawName);
		Optional<Long> found = exact(name);
		if (found.isPresent()) {
			return found;
		}
		String withoutParentheses = PARENTHESIZED.matcher(name).replaceAll("").strip();
		if (!withoutParentheses.isEmpty() && !withoutParentheses.equals(name)) {
			return exact(withoutParentheses);
		}
		return Optional.empty();
	}

	private Optional<Long> exact(String name) {
		Long id = idsByStandardName.get(name);
		if (id == null) {
			id = idsByNormalizedAlias.get(IngredientNames.normalize(name));
		}
		return Optional.ofNullable(id);
	}
}
