package com.aibeauty.api.global.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * SMTP로 실제 메일을 보낸다 (Gmail SMTP, FIX-008). app.mail.provider=smtp 일 때만 등록된다.
 * 서버·계정은 spring.mail.*(MAIL_USERNAME, MAIL_PASSWORD 환경 변수)로 설정한다.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp")
public class SmtpAuthMailSender implements AuthMailSender {

	private final JavaMailSender javaMailSender;
	private final MailProperties properties;

	public SmtpAuthMailSender(JavaMailSender javaMailSender, MailProperties properties) {
		if (properties.from() == null || properties.from().isBlank()) {
			throw new IllegalStateException("app.mail.provider=smtp이면 보내는 주소(MAIL_USERNAME 또는 MAIL_FROM)가 필요합니다.");
		}
		this.javaMailSender = javaMailSender;
		this.properties = properties;
	}

	@Override
	public void sendPasswordResetLink(String email, String resetLink) {
		send(email, "[pickly] 비밀번호 재설정 안내", """
				pickly 비밀번호 재설정을 요청하셨어요.
				아래 링크를 눌러 새 비밀번호를 설정해 주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.

				%s

				직접 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.
				""".formatted(resetLink));
	}

	@Override
	public void sendVerificationCode(String email, String code) {
		send(email, "[pickly] 인증 코드 " + code, """
				pickly 회원가입 인증 코드예요.

				인증 코드: %s

				가입 화면에 이 코드를 입력해 주세요. 코드는 10분 동안 유효해요.
				직접 요청하지 않았다면 이 메일을 무시해 주세요.
				""".formatted(code));
	}

	private void send(String to, String subject, String text) {
		try {
			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			helper.setFrom(properties.from(), properties.fromName());
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(text);
			javaMailSender.send(message);
		}
		catch (MessagingException | UnsupportedEncodingException | MailException e) {
			throw new IllegalStateException("메일 발송에 실패했습니다.", e);
		}
	}
}
