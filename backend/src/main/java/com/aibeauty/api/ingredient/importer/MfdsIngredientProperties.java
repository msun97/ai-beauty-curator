package com.aibeauty.api.ingredient.importer;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 식약처 화장품 원료성분정보 Open API 설정.
 *
 * @param apiKey 공공데이터포털 인증키. 저장소에 올리지 않고 환경 변수(MFDS_API_KEY)나 backend/.env.local로 넣는다
 * @param baseUrl 오퍼레이션 URL
 * @param pageSize 한 번에 받을 건수 (API 최대 500)
 * @param requestInterval 요청 사이 대기 시간
 * @param rawDir 원본 응답을 남길 폴더. 비우면 남기지 않는다
 */
@ConfigurationProperties(prefix = "app.import.mfds-ingredients")
public record MfdsIngredientProperties(
		String apiKey,
		String baseUrl,
		int pageSize,
		Duration requestInterval,
		String rawDir) {

	public static final int MAX_PAGE_SIZE = 500;

	public MfdsIngredientProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://apis.data.go.kr/1471000/CsmtcsIngdCpntInfoService01/getCsmtcsIngdCpntInfoService01";
		}
		if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
			pageSize = MAX_PAGE_SIZE;
		}
		if (requestInterval == null) {
			requestInterval = Duration.ofMillis(300);
		}
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
