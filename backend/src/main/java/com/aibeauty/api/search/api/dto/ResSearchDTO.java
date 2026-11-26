package com.aibeauty.api.search.api.dto;

import java.util.List;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;

/**
 * 통합 검색 결과 (FR-SRC-01~03). 상품과 성분을 나누어 준다.
 *
 * @param products 필터를 적용한 상품. 무한 스크롤로 다음 페이지를 이어 받는다
 * @param ingredients 이름이 일치하는 성분. 첫 페이지에서만 채운다
 * @param suggestions 상품과 성분이 모두 없을 때 대신 보여줄 인기 상품. 그 밖에는 빈 목록
 */
public record ResSearchDTO(
		String query,
		ResPageDTO<ResProductCardDTO> products,
		List<Ingredient> ingredients,
		List<ResProductCardDTO> suggestions) {

	/**
	 * @param matchedAlias 별칭으로 찾았을 때 그 별칭. 화면에 "'○○'(으)로 검색한 성분이에요"로 안내한다
	 */
	public record Ingredient(long id, String standardName, String matchedAlias, long productCount) {
	}
}
