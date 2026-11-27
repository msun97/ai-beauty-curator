package com.aibeauty.api.auth.api.dto;

import com.aibeauty.api.auth.application.AuthService.SignupCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 (FIX-008). 이메일은 받지 않고, 인증 코드 확인에서 받은 1회용 토큰으로 인증된 이메일을 찾는다.
 */
public record ReqSignupDTO(
		@NotBlank(message = "이메일 인증을 먼저 진행해 주세요.")
		@Size(max = 100, message = "이메일 인증 정보가 올바르지 않아요.")
		String verificationToken,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
		String password,

		@Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
		String nickname,

		@NotNull(message = "약관 동의 정보가 필요해요.")
		@Valid
		Agreements agreements) {

	/** 서비스 이용약관, 개인정보 수집·이용, 피부 정보 수집·이용(별도 항목) */
	public record Agreements(boolean terms, boolean privacy, boolean skinData) {
	}

	public SignupCommand toCommand() {
		return new SignupCommand(verificationToken, password, nickname, agreements.terms(), agreements.privacy(),
				agreements.skinData());
	}
}
