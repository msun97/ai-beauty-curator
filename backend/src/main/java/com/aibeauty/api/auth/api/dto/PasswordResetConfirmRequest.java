package com.aibeauty.api.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
		@NotBlank(message = "재설정 토큰이 필요해요.") String token,

		@NotBlank(message = "새 비밀번호를 입력해 주세요.")
		@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
		String newPassword) {
}
