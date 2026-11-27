package com.aibeauty.api.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가입 인증 코드 확인 (FIX-008) */
public record ReqEmailVerificationConfirmDTO(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않아요.")
		@Size(max = 254, message = "이메일이 너무 길어요.")
		String email,

		@NotBlank(message = "인증 코드를 입력해 주세요.")
		@Pattern(regexp = "[0-9]{6}", message = "인증 코드 6자리 숫자를 입력해 주세요.")
		String code) {
}
