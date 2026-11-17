package com.aibeauty.api.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
	CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN, "허용되지 않은 요청이에요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),

	// 회원·인증
	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
	REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요."),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
	INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "링크가 만료됐거나 이미 사용됐어요. 비밀번호 찾기를 다시 진행해 주세요.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
