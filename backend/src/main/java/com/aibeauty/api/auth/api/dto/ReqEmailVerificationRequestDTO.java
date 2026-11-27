package com.aibeauty.api.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 가입 인증 코드 요청 (FIX-008) */
public record ReqEmailVerificationRequestDTO(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않아요.")
		@Size(max = 254, message = "이메일이 너무 길어요.")
		String email) {
}
