package com.aibeauty.api.global.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용: 메일을 보내지 않고 재설정 링크·인증 코드를 로그로 출력한다.
 * 링크와 코드가 그대로 남으므로 운영 환경에서는 절대 사용하지 않는다 (NFR-PRIV-02).
 * app.mail.provider=log 일 때만 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "log")
public class LoggingAuthMailSender implements AuthMailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingAuthMailSender.class);

	@Override
	public void sendPasswordResetLink(String email, String resetLink) {
		log.info("[로컬 전용] 비밀번호 재설정 메일 대신 링크 출력: to={}, link={}", email, resetLink);
	}

	@Override
	public void sendVerificationCode(String email, String code) {
		log.info("[로컬 전용] 인증 코드 메일 대신 코드 출력: to={}, code={}", email, code);
	}
}
