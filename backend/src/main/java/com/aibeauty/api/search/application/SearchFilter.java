package com.aibeauty.api.search.application;

/**
 * 검색 필터 (FR-SRC-02). null이면 적용하지 않는다.
 *
 * @param categoryCode 카테고리 코드
 * @param brandId 브랜드
 * @param minPrice 최소 가격(원, 포함)
 * @param maxPrice 최대 가격(원, 포함)
 * @param excludeAvoided 로그인 사용자의 기피 성분이 든 상품 제외
 */
public record SearchFilter(String categoryCode, Long brandId, Integer minPrice, Integer maxPrice,
		boolean excludeAvoided) {

	public static SearchFilter none() {
		return new SearchFilter(null, null, null, null, false);
	}
}
