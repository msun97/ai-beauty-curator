package com.aibeauty.api.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용: 메일을 보내지 않고 재설정 링크를 로그로 출력한다.
 * 링크에 토큰이 포함되므로 운영 환경에서는 절대 사용하지 않는다 (NFR-PRIV-02).
 * app.mail.provider=log 일 때만 등록되며, 운영 설정에는 이 값이 없어 메일 구현이 없으면 기동에 실패한다.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "log")
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailSender.class);

	@Override
	public void send(String email, String resetLink) {
		log.info("[로컬 전용] 비밀번호 재설정 메일 대신 링크 출력: to={}, link={}", email, resetLink);
	}
}
