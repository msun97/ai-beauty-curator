package com.aibeauty.api.ingredient.importer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 식약처 화장품 원료성분정보 API 호출.
 * 요청 URL에 인증키가 들어가므로 URL을 로그나 예외 메시지에 남기지 않는다.
 */
public class MfdsIngredientClient {

	private static final Logger log = LoggerFactory.getLogger(MfdsIngredientClient.class);
	private static final String SUCCESS_CODE = "00";
	private static final int MAX_ATTEMPTS = 3;

	public record Page(int pageNo, int totalCount, List<MfdsIngredientItem> items, String rawJson) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(Header header, Body body) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Header(String resultCode, String resultMsg) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(Integer pageNo, Integer totalCount, List<MfdsIngredientItem> items) {
	}

	private final MfdsIngredientProperties properties;
	private final JsonMapper jsonMapper;
	private final HttpClient httpClient;

	public MfdsIngredientClient(MfdsIngredientProperties properties, JsonMapper jsonMapper) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	}

	public Page fetch(int pageNo) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return parse(pageNo, request(pageNo));
			} catch (MfdsApiException e) {
				if (!e.retryable()) {
					throw e;
				}
				last = e;
			}
			log.warn("성분 API {}페이지 요청 실패 ({}/{}): {}", pageNo, attempt, MAX_ATTEMPTS, last.getMessage());
			sleep(Duration.ofSeconds(attempt * 2L));
		}
		throw last;
	}

	private String request(int pageNo) {
		URI uri = URI.create(properties.baseUrl()
				+ "?serviceKey=" + encodedKey()
				+ "&pageNo=" + pageNo
				+ "&numOfRows=" + properties.pageSize()
				+ "&type=json");
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build();
		try {
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new MfdsApiException("HTTP " + response.statusCode(), response.statusCode() >= 500);
			}
			return new String(response.body(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			// 예외 메시지에 URL이 들어갈 수 있어 종류만 남긴다
			throw new MfdsApiException("네트워크 오류: " + e.getClass().getSimpleName(), true);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MfdsApiException("요청이 중단되었습니다", false);
		}
	}

	Page parse(int pageNo, String rawJson) {
		Response response;
		try {
			response = jsonMapper.readValue(rawJson, Response.class);
		} catch (JacksonException e) {
			// 인증 오류 등은 JSON이 아닌 XML로 온다
			throw new MfdsApiException("예상하지 못한 응답: " + abbreviate(rawJson), false);
		}
		if (response.header() == null) {
			throw new MfdsApiException("예상하지 못한 응답: " + abbreviate(rawJson), false);
		}
		if (!SUCCESS_CODE.equals(response.header().resultCode())) {
			throw new MfdsApiException(
					"API 오류 " + response.header().resultCode() + ": " + response.header().resultMsg(), false);
		}
		Body body = response.body();
		List<MfdsIngredientItem> items = body == null || body.items() == null ? List.of() : body.items();
		int totalCount = body == null || body.totalCount() == null ? 0 : body.totalCount();
		return new Page(pageNo, totalCount, items, rawJson);
	}

	/** 포털이 보여주는 키가 이미 인코딩된 형태(% 포함)면 그대로, 아니면 인코딩해서 쓴다. */
	private String encodedKey() {
		String key = properties.apiKey().strip();
		return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
	}

	private static String abbreviate(String value) {
		String oneLine = value.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
	}

	static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new MfdsApiException("요청이 중단되었습니다", false);
		}
	}

	public static class MfdsApiException extends RuntimeException {

		private final boolean retryable;

		MfdsApiException(String message, boolean retryable) {
			super(message);
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}
	}
}
