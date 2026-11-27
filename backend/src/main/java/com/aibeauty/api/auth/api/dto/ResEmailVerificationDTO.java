package com.aibeauty.api.auth.api.dto;

import com.aibeauty.api.auth.application.EmailVerificationService.Confirmation;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 가입 인증 코드 확인 결과 (FIX-008).
 * registered=true: 이미 가입된 이메일 / registered=false: 새 이메일, verificationToken으로 가입 (30분, 1회)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResEmailVerificationDTO(boolean registered, String verificationToken) {

	public static ResEmailVerificationDTO from(Confirmation confirmation) {
		return new ResEmailVerificationDTO(confirmation.registered(), confirmation.verificationToken());
	}
}
