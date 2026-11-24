package com.aibeauty.api.global.api;

import java.util.List;
import java.util.function.Function;

/**
 * 무한 스크롤용 목록 응답. 전체 개수 대신 다음 페이지 여부만 준다(count 쿼리 생략).
 *
 * @param page 0부터 시작
 */
public record ResPageDTO<T>(List<T> items, int page, int size, boolean hasNext) {

	public <R> ResPageDTO<R> map(Function<T, R> mapper) {
		return new ResPageDTO<>(items.stream().map(mapper).toList(), page, size, hasNext);
	}
}
