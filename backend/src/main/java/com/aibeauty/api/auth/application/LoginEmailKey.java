package com.aibeauty.api.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.aibeauty.api.global.security.AuthProperties;

/**
 * 로그인 실패 기록(login_attempts)의 키를 만든다 (FIX-006).
 *
 * 가입하지 않은 이메일도 실패를 기록하므로 이메일 원문을 저장하지 않는다.
 * 정규화한 이메일을 서버 비밀키로 HMAC-SHA256한 값(16진수 64자)을 쓴다.
 * 비밀키를 섞으므로 DB만 유출돼서는 흔한 이메일 목록을 대입해 원문을 알아내기 어렵다.
 */
@Component
public class LoginEmailKey {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public LoginEmailKey(AuthProperties properties) {
		this.key = new SecretKeySpec(properties.login().emailKeySecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	/** 대소문자·앞뒤 공백이 달라도 같은 이메일이면 같은 키 */
	public String of(String rawEmail) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] digest = mac.doFinal(AuthService.normalizeEmail(rawEmail).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HmacSHA256을 사용할 수 없습니다.", e);
		}
	}
}
