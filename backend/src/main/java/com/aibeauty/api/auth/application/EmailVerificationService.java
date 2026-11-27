package com.aibeauty.api.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.auth.domain.AuthErrorCode;
import com.aibeauty.api.auth.domain.EmailVerification;
import com.aibeauty.api.auth.domain.EmailVerificationRepository;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.mail.AuthMailSender;
import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.global.security.EmailVerificationProperties;
import com.aibeauty.api.global.security.Hmac;
import com.aibeauty.api.global.security.TokenGenerator;
import com.aibeauty.api.user.domain.UserRepository;

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
	private final UserRepository userRepository;
	private final AuthMailSender mailSender;
	private final EmailVerificationProperties properties;
	private final SecretKeySpec codeKey;
	private final Clock clock;

	public EmailVerificationService(EmailVerificationRepository repository, UserRepository userRepository,
			AuthMailSender mailSender, EmailVerificationProperties properties, AuthProperties authProperties,
			Clock clock) {
		this.repository = repository;
		this.userRepository = userRepository;
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

	/**
	 * 코드를 확인한다. 맞힌 사람(= 메일함 주인)에게만 가입 여부를 알려준다.
	 * - 가입된 이메일: registered=true (화면은 "이미 가입된 이메일" 안내)
	 * - 새 이메일: registered=false + 가입용 1회용 토큰(30분)
	 * 틀리면 실패 횟수를 늘리고, 허용 횟수에 닿으면 이 코드는 더 쓸 수 없다(새 코드를 받아야 함).
	 * 실패 횟수는 예외를 던져도 저장되어야 하므로 BusinessException에는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public Confirmation confirmCode(String rawEmail, String code) {
		String email = AuthService.normalizeEmail(rawEmail);
		Instant now = clock.instant();
		EmailVerification verification = repository.findByEmailForUpdate(email)
			.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE));
		if (verification.attemptsExceeded(properties.maxFailures())) {
			throw attemptsExceeded();
		}
		if (!verification.isCodeUsable(now)) {
			throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
		}
		if (!matches(codeHash(email, code), verification.getCodeHash())) {
			verification.recordFailure(now);
			throw verification.attemptsExceeded(properties.maxFailures()) ? attemptsExceeded()
					: new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
		}

		verification.consumeCode(now);
		if (userRepository.existsByEmail(email)) {
			return Confirmation.alreadyRegistered();
		}
		String token = TokenGenerator.newToken();
		verification.issueToken(TokenGenerator.hash(token), now.plus(properties.tokenTtl()), now);
		return Confirmation.newEmail(token);
	}

	/**
	 * 가입용 토큰을 사용 처리하고 인증된 이메일을 돌려준다. 가입과 같은 트랜잭션에서 불러,
	 * 가입이 실패하면 토큰도 사용되지 않은 상태로 되돌아가게 한다.
	 * 토큰이 없거나 만료(30분)·이미 사용됐으면 INVALID_VERIFICATION_TOKEN.
	 */
	@Transactional
	public String consumeToken(String verificationToken) {
		Instant now = clock.instant();
		EmailVerification verification = repository.findByTokenHashForUpdate(TokenGenerator.hash(verificationToken))
			.filter(v -> v.isTokenUsable(now))
			.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_VERIFICATION_TOKEN));
		verification.markTokenUsed(now);
		return verification.getEmail();
	}

	/**
	 * 오래된 인증 기록 정리: 코드·토큰이 모두 만료됐거나 토큰을 이미 썼고, 마지막 변경이 staleBefore보다 이전인 행.
	 * @return 지운 행 수
	 */
	@Transactional
	public int deleteStale(Instant staleBefore, Instant now) {
		return repository.deleteStale(staleBefore, now);
	}

	/** 해시 비교 시간이 값에 따라 달라지지 않게 한다 */
	private static boolean matches(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	private BusinessException attemptsExceeded() {
		return BusinessException.withArgs(AuthErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED, properties.maxFailures());
	}

	/** registered=false일 때만 verificationToken이 있다 */
	public record Confirmation(boolean registered, String verificationToken) {
		static Confirmation alreadyRegistered() {
			return new Confirmation(true, null);
		}

		static Confirmation newEmail(String verificationToken) {
			return new Confirmation(false, verificationToken);
		}
	}

	/** 000000~999999 (앞자리 0 포함 6자리) */
	static String newCode() {
		return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
	}

	String codeHash(String normalizedEmail, String code) {
		return Hmac.sha256Hex(codeKey, CODE_HASH_PREFIX + normalizedEmail + ":" + code);
	}
}
