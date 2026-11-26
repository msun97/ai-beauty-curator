package com.aibeauty.api.product.application;

/** 상품 목록 정렬 (FR-PRD-01). 동점이면 최근 등록 상품이 먼저 온다. */
public enum ProductSort {
	/** 최근 30일 서비스 내 행동(조회·북마크·비교·공유) 수 */
	POPULAR,
	LATEST,
	/** 가격 미상 상품은 맨 뒤 */
	PRICE_ASC,
	PRICE_DESC
}
