package com.aibeauty.api.global.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인과 관계없이 쓰는 공통 오류 코드.
 */
public enum CommonErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	INVALID_TYPE(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않아요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
	CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN, "허용되지 않은 요청이에요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
	CONFLICT(HttpStatus.CONFLICT, "다른 요청과 충돌했어요. 잠시 후 다시 시도해 주세요."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식이에요."),
	/** 요청 횟수 제한 초과 (FIX-009). 가입 여부와 관계없이 같은 문구 */
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	CommonErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

	/** 컨트롤러 밖에서 상태 코드만 알 때(/error) 가장 가까운 공통 코드를 고른다. */
	public static CommonErrorCode fromStatus(int status) {
		return switch (status) {
			case 400 -> INVALID_INPUT;
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 409 -> CONFLICT;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			default -> INTERNAL_ERROR;
		};
	}
}
