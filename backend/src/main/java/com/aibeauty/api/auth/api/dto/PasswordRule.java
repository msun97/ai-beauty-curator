package com.aibeauty.api.auth.api.dto;

/**
 * 비밀번호 규칙: 영문·숫자를 포함한 8~64자 (FR-AUTH-01). 가입과 재설정에 같은 규칙을 쓴다.
 */
public final class PasswordRule {

	public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
	public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요.";

	private PasswordRule() {
	}
}
