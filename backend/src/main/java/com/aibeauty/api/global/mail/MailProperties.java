package com.aibeauty.api.global.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 설정 (app.mail).
 * provider: log | smtp, from: 보내는 주소(Gmail SMTP는 로그인한 계정 주소와 같아야 함), fromName: 보내는 사람 이름
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String provider, String from, String fromName) {
}
