package com.aibeauty.api.auth.api.dto;

import com.aibeauty.api.auth.application.AuthService.SignupCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReqSignupDTO(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않아요.")
		@Size(max = 254, message = "이메일이 너무 길어요.")
		String email,

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
		return new SignupCommand(email, password, nickname, agreements.terms(), agreements.privacy(),
				agreements.skinData());
	}
}
