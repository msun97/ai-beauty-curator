package com.aibeauty.api.global.mail;

/**
 * 인증 관련 메일 발송 (비밀번호 재설정 링크, 가입 인증 코드).
 *
 * app.mail.provider 값에 따라 구현이 하나만 등록된다.
 * - log: 메일 대신 로그로 출력 (로컬·테스트 전용)
 * - smtp: SMTP로 실제 발송 (Gmail SMTP, FIX-008)
 * 운영 설정에 provider가 없으면 구현이 없어 기동에 실패한다 (메일 없이 배포되는 실수 방지).
 */
public interface AuthMailSender {

	void sendPasswordResetLink(String email, String resetLink);

	/** @param code 6자리 인증 코드 */
	void sendVerificationCode(String email, String code);
}
