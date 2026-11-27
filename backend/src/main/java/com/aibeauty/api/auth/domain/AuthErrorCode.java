package com.aibeauty.api.auth.domain;

import org.springframework.http.HttpStatus;

import com.aibeauty.api.global.error.ErrorCode;

/**
 * 회원·인증 도메인 오류 코드.
 */
public enum AuthErrorCode implements ErrorCode {

	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요.", "email"),
	REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 모두 동의해 주세요.", "agreements"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요."),
	/** {0}: 최대 실패 횟수, {1}: 잠금 시간. 둘 다 설정값(app.auth.login)으로 채운다 */
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS,
			"로그인을 {0}번 잘못 시도해서 {1} 동안 로그인이 제한돼요. "
					+ "{1} 뒤에 다시 시도하거나, 비밀번호를 재설정하면 바로 로그인할 수 있어요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
	INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "링크가 만료됐거나 이미 사용됐어요. 비밀번호 찾기를 다시 진행해 주세요."),
	/** 인증 코드가 틀렸거나 만료됨 (FIX-008). 화면은 인증 코드 입력란에 붙인다 */
	INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않거나 만료됐어요. 메일의 코드를 다시 확인해 주세요.",
			"code"),
	/** {0}: 코드 입력 실패 허용 횟수 (app.auth.email-verification.max-failures) */
	VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
			"인증 코드를 {0}번 잘못 입력했어요. 인증 코드를 다시 받아 주세요.");

	private final HttpStatus status;
	private final String message;
	private final String field;

	AuthErrorCode(HttpStatus status, String message) {
		this(status, message, null);
	}

	/** field: 화면에서 이 오류를 붙일 입력 항목 (요청 필드명) */
	AuthErrorCode(HttpStatus status, String message, String field) {
		this.status = status;
		this.message = message;
		this.field = field;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public String field() {
		return field;
	}
}
