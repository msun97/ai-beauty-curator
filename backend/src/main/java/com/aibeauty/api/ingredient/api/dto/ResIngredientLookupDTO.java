package com.aibeauty.api.ingredient.api.dto;

/**
 * 이름으로 찾은 표준 성분 (FR-ING-02).
 *
 * @param matchedName 입력과 일치한 이름. 별칭으로 찾았으면 화면에 "'○○'(으)로 검색한 성분이에요"로 안내한다
 */
public record ResIngredientLookupDTO(long id, String standardName, String matchedName, MatchedBy matchedBy) {

	public enum MatchedBy {
		STANDARD_NAME, ALIAS
	}
}
