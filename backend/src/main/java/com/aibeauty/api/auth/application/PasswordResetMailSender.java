package com.aibeauty.api.auth.application;

/**
 * 비밀번호 재설정 메일 발송. 운영 환경용 구현(SMTP 또는 메일 발송 서비스)은 배포 단계에서 추가한다.
 */
public interface PasswordResetMailSender {

	void send(String email, String resetLink);
}
