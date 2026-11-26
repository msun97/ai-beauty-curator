package com.aibeauty.api.ingredient.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 식약처 화장품 원료성분정보 API의 성분 한 건. 필드명은 API 응답 그대로다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfdsIngredientItem(
		@JsonProperty("INGR_KOR_NAME") String koreanName,
		@JsonProperty("INGR_ENG_NAME") String englishName,
		@JsonProperty("CAS_NO") String casNo,
		@JsonProperty("ORIGIN_MAJOR_KOR_NAME") String origin,
		@JsonProperty("INGR_SYNONYM") String synonyms) {
}
