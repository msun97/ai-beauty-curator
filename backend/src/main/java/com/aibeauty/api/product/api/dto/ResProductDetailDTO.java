package com.aibeauty.api.product.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.aibeauty.api.product.application.DataIssue;
import com.aibeauty.api.product.domain.SkinType;

/**
 * 상품 상세 (FR-PRD-02). 궁합 결과, 리뷰 요약, 대안 추천은 각 기능에서 별도 API로 제공한다.
 */
public record ResProductDetailDTO(
		long id,
		String name,
		Brand brand,
		Category category,
		String description,
		String imageUrl,
		String volume,
		Integer price,
		Instant priceUpdatedAt,
		List<SkinType> skinTypes,
		List<Concern> concerns,
		List<Ingredient> ingredients,
		DataStatus dataStatus,
		Source source) {

	public record Brand(long id, String name) {
	}

	public record Category(String code, String name) {
	}

	public record Concern(String code, String name) {
	}

	/**
	 * 전성분 한 줄.
	 *
	 * @param order 표시 순서(1부터)
	 * @param rawName 제품에 표기된 이름 그대로
	 * @param ingredientId 매칭된 표준 성분. 매칭 전이면 null
	 */
	public record Ingredient(int order, String rawName, Long ingredientId, String standardName) {
	}

	/** issues가 비어 있으면 궁합을 판정할 수 있는 상태다 (FR-PRD-03) */
	public record DataStatus(boolean sufficient, List<DataIssue> issues) {
	}

	/** 상품 정보 출처와 기준일 */
	public record Source(String name, String url, LocalDate collectedAt) {
	}
}
