package com.aibeauty.api.global.error;

import java.util.List;

import com.aibeauty.api.global.trace.TraceIdFilter;

/**
 * 모든 API의 공통 오류 응답 형식 (NFR-REL-02).
 *
 * @param code 클라이언트 분기용 오류 코드
 * @param message 사용자에게 보여줄 문구
 * @param fieldErrors 입력값 오류 목록 (없으면 빈 배열)
 * @param traceId 요청 추적 ID. 사용자가 문의할 때 이 값으로 서버 로그를 찾는다 (NFR-REL-04)
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, String traceId) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
		return new ErrorResponse(errorCode.code(), errorCode.message(), List.copyOf(fieldErrors),
				TraceIdFilter.currentTraceId());
	}
}
