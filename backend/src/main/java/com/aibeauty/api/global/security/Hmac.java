package com.aibeauty.api.global.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 서버 비밀키로 만드는 HMAC-SHA256 (16진수 64자). DB만 유출돼서는 원문을 대입해 맞혀 보기 어렵게 한다. */
public final class Hmac {

	private static final String ALGORITHM = "HmacSHA256";

	private Hmac() {
	}

	public static SecretKeySpec key(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	public static String sha256Hex(SecretKeySpec key, String value) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HmacSHA256을 사용할 수 없습니다.", e);
		}
	}
}
