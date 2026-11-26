package com.aibeauty.api.product.api.dto;

import com.aibeauty.api.product.application.ProductCard;

/**
 * 상품 카드. 궁합 배지는 추천 기능에서 로그인 사용자에게만 덧붙인다.
 *
 * @param dataSufficient false면 "데이터 부족"으로 표시한다 (FR-PRD-03)
 */
public record ResProductCardDTO(
		long id,
		String name,
		String brandName,
		String categoryCode,
		String imageUrl,
		Integer price,
		String volume,
		boolean dataSufficient) {

	public static ResProductCardDTO from(ProductCard card) {
		return new ResProductCardDTO(card.id(), card.name(), card.brandName(), card.categoryCode(), card.imageUrl(),
				card.price(), card.volume(), card.dataSufficient());
	}
}
