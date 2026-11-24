package com.aibeauty.api.product.application;

/**
 * 목록 카드 한 장.
 *
 * @param dataSufficient 전성분이 완전하고 모두 표준 성분에 매칭됐는지. false면 "데이터 부족" (FR-PRD-03)
 */
public record ProductCard(
		long id,
		String name,
		String brandName,
		String categoryCode,
		String imageUrl,
		Integer price,
		String volume,
		boolean dataSufficient) {
}
