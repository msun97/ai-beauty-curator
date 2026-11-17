package com.aibeauty.api.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh Token, 비밀번호 재설정 토큰처럼 DB에 해시로만 저장하는 무작위 토큰을 만든다.
 */
public final class TokenGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private TokenGenerator() {
	}

	/** 256bit 무작위 값을 URL에 안전한 문자열로 반환한다. */
	public static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** DB 저장·조회용 SHA-256 해시 (16진수 64자). */
	public static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}
}
