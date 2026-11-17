package com.aibeauty.api.global.error;

import java.util.List;

/**
 * 모든 API의 공통 오류 응답 형식 (NFR-REL-02).
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.message(), List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
		return new ErrorResponse(errorCode.name(), errorCode.message(), List.copyOf(fieldErrors));
	}
}
