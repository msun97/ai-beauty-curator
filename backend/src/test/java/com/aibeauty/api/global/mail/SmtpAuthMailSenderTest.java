package com.aibeauty.api.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class SmtpAuthMailSenderTest {

	private static final MailProperties PROPERTIES = new MailProperties("smtp", "sender@example.com", "pickly");

	@Test
	@DisplayName("인증 코드 메일: 받는 사람, 보내는 사람(pickly), 제목·본문에 코드")
	void verificationCode() throws Exception {
		JavaMailSender javaMailSender = mock(JavaMailSender.class);
		when(javaMailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

		new SmtpAuthMailSender(javaMailSender, PROPERTIES).sendVerificationCode("user@example.com", "123456");

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(javaMailSender).send(sent.capture());
		MimeMessage message = sent.getValue();
		assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("user@example.com");
		InternetAddress from = (InternetAddress) message.getFrom()[0];
		assertThat(from.getAddress()).isEqualTo("sender@example.com");
		assertThat(from.getPersonal()).isEqualTo("pickly");
		assertThat(message.getSubject()).contains("123456");
		assertThat((String) message.getContent()).contains("123456").contains("10분");
	}

	@Test
	@DisplayName("비밀번호 재설정 메일: 본문에 링크")
	void passwordReset() throws Exception {
		JavaMailSender javaMailSender = mock(JavaMailSender.class);
		when(javaMailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

		new SmtpAuthMailSender(javaMailSender, PROPERTIES)
			.sendPasswordResetLink("user@example.com", "http://localhost:3100/reset-password?token=abc");

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(javaMailSender).send(sent.capture());
		assertThat((String) sent.getValue().getContent()).contains("reset-password?token=abc");
	}

	@Test
	@DisplayName("보내는 주소가 없으면 기동 실패")
	void requiresFrom() {
		assertThatThrownBy(() -> new SmtpAuthMailSender(mock(JavaMailSender.class), new MailProperties("smtp", "", "pickly")))
			.isInstanceOf(IllegalStateException.class);
	}
}
