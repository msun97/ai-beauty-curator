package com.aibeauty.api.auth.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.auth.domain.EmailVerification;
import com.aibeauty.api.auth.domain.EmailVerificationRepository;
import com.aibeauty.api.global.mail.AuthMailSender;
import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.global.security.EmailVerificationProperties;
import com.aibeauty.api.global.security.Hmac;

/**
 * 가입 이메일 인증 코드 (FIX-008, FR-AUTH-01).
 *
 * 가입 여부는 코드를 맞힌 사람(= 메일함 주인)에게만 알려준다.
 * 그래서 코드 요청 단계에서는 회원 테이블을 아예 조회하지 않고, 모든 이메일을 똑같이 처리한다
 * (같은 응답, 같은 DB 작업, 같은 메일 발송 → 응답과 응답 시간으로 가입 여부를 알 수 없음).
 */
@Service
public class EmailVerificationService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int CODE_BOUND = 1_000_000;
	/** 코드 해시용 HMAC 입력 앞에 붙여, 같은 비밀키를 쓰는 다른 용도(로그인 실패 기록 키)와 섞이지 않게 한다 */
	private static final String CODE_HASH_PREFIX = "email-verification-code:";

	private final EmailVerificationRepository repository;
	private final AuthMailSender mailSender;
	private final EmailVerificationProperties properties;
	private final SecretKeySpec codeKey;
	private final Clock clock;

	public EmailVerificationService(EmailVerificationRepository repository, AuthMailSender mailSender,
			EmailVerificationProperties properties, AuthProperties authProperties, Clock clock) {
		this.repository = repository;
		this.mailSender = mailSender;
		this.properties = properties;
		this.codeKey = Hmac.key(authProperties.login().emailKeySecret());
		this.clock = clock;
	}

	/**
	 * 6자리 코드를 만들어 메일로 보낸다. 가입 여부와 관계없이 똑같이 동작한다.
	 * 마지막 발송 후 재발송 간격(60초)이 지나지 않았으면 아무것도 하지 않는다(호출하는 쪽 응답은 같음).
	 */
	@Transactional
	public void requestCode(String rawEmail) {
		String email = AuthService.normalizeEmail(rawEmail);
		Instant now = clock.instant();
		repository.insertIfAbsent(email, now);
		EmailVerification verification = repository.findByEmailForUpdate(email)
			.orElseThrow(() -> new IllegalStateException("인증 행을 만들지 못했습니다."));
		if (!verification.canResend(now, properties.resendInterval())) {
			return;
		}

		String code = newCode();
		verification.reissueCode(codeHash(email, code), now.plus(properties.codeTtl()), now);
		mailSender.sendVerificationCode(email, code);
	}

	/** 000000~999999 (앞자리 0 포함 6자리) */
	static String newCode() {
		return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
	}

	String codeHash(String normalizedEmail, String code) {
		return Hmac.sha256Hex(codeKey, CODE_HASH_PREFIX + normalizedEmail + ":" + code);
	}
}
