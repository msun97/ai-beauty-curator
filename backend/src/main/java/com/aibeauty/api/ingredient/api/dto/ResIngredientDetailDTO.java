package com.aibeauty.api.ingredient.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 성분 상세 (FR-ING-01).
 *
 * @param aliases 표준명과 표기만 다른 이름(띄어쓰기·기호 차이)은 뺀 다른 이름 목록
 * @param description 일반적 특성. 화면에는 "개인 반응은 다를 수 있다"는 안내와 함께 표시한다 (FR-ING-03)
 * @param productCount 이 성분이 들어간 상품 수
 */
public record ResIngredientDetailDTO(
		long id,
		String standardName,
		String inciName,
		List<String> aliases,
		String description,
		Source source,
		long productCount) {

	/** 성분 정보 출처와 확인일 */
	public record Source(String name, String url, LocalDate verifiedAt) {
	}
}
