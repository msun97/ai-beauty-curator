package com.aibeauty.api.auth.domain;

import org.springframework.http.HttpStatus;

import com.aibeauty.api.global.error.ErrorCode;

/**
 * 회원·인증 도메인 오류 코드.
 */
public enum AuthErrorCode implements ErrorCode {

	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
	REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요."),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
	INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "링크가 만료됐거나 이미 사용됐어요. 비밀번호 찾기를 다시 진행해 주세요.");

	private final HttpStatus status;
	private final String message;

	AuthErrorCode(HttpStatus status, String message) {
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
}
