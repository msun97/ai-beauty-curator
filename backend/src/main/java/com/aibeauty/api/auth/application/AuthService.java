package com.aibeauty.api.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.auth.domain.ConsentType;
import com.aibeauty.api.auth.domain.RevokedReason;
import com.aibeauty.api.auth.domain.UserConsent;
import com.aibeauty.api.auth.domain.UserConsentRepository;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.format.KoreanDuration;
import com.aibeauty.api.auth.domain.AuthErrorCode;
import com.aibeauty.api.global.security.AccessTokenProvider;
import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.user.domain.User;
import com.aibeauty.api.user.domain.UserRepository;

@Service
public class AuthService {

	private static final int NICKNAME_MAX_LENGTH = 20;

	private final UserRepository userRepository;
	private final UserConsentRepository userConsentRepository;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenProvider accessTokenProvider;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptService loginAttemptService;
	private final LoginEmailKey loginEmailKey;
	private final AuthProperties properties;
	private final Clock clock;

	/** 없는 이메일로 로그인할 때도 비밀번호 비교 시간을 들여, 응답 시간으로 가입 여부를 알 수 없게 한다. */
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, UserConsentRepository userConsentRepository,
			RefreshTokenService refreshTokenService, AccessTokenProvider accessTokenProvider,
			PasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService, LoginEmailKey loginEmailKey,
			AuthProperties properties, Clock clock) {
		this.userRepository = userRepository;
		this.userConsentRepository = userConsentRepository;
		this.refreshTokenService = refreshTokenService;
		this.accessTokenProvider = accessTokenProvider;
		this.passwordEncoder = passwordEncoder;
		this.loginAttemptService = loginAttemptService;
		this.loginEmailKey = loginEmailKey;
		this.properties = properties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional(readOnly = true)
	public boolean isEmailAvailable(String email) {
		return !userRepository.existsByEmail(normalizeEmail(email));
	}

	/** 회원가입 후 바로 로그인 상태로 만든다 (가입 전 입력한 프로필을 이어서 저장하기 위해 - FR-PRF-05). */
	@Transactional
	public AuthResult signup(SignupCommand command) {
		if (!command.agreedTerms() || !command.agreedPrivacy() || !command.agreedSkinData()) {
			throw new BusinessException(AuthErrorCode.REQUIRED_CONSENT_MISSING);
		}
		String email = normalizeEmail(command.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
		}

		Instant now = clock.instant();
		User user;
		try {
			user = userRepository.saveAndFlush(User.create(email, passwordEncoder.encode(command.password()),
					resolveNickname(command.nickname(), email), now));
		}
		catch (DataIntegrityViolationException e) {
			// 중복 확인과 저장 사이에 같은 이메일로 동시에 가입한 경우
			throw new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
		}

		String version = properties.termsVersion();
		userConsentRepository.saveAll(List.of(
				UserConsent.agree(user, ConsentType.TERMS, version, now),
				UserConsent.agree(user, ConsentType.PRIVACY, version, now),
				UserConsent.agree(user, ConsentType.SKIN_DATA, version, now)));

		return issueTokens(user);
	}

	/**
	 * 실패 원인(이메일 없음, 비밀번호 틀림)을 구분하지 않고 같은 오류를 반환한다 (FR-AUTH-02).
	 *
	 * 가입 여부가 드러나지 않도록 두 경우가 같은 길을 걷는다 (FIX-006).
	 * - 실패 기록·잠금은 회원 행이 아니라 이메일 기준(login_attempts)이라, 없는 이메일도 똑같이 5번째에 잠긴다.
	 * - 없는 이메일도 가짜 해시로 비밀번호를 비교하고, 실패 기록 쓰기도 똑같이 한 번 해서 응답 시간이 같다.
	 * 실패 기록은 예외를 던져도 저장되어야 하므로 BusinessException에는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public AuthResult login(String rawEmail, String password) {
		Instant now = clock.instant();
		String emailKey = loginEmailKey.of(rawEmail);
		if (loginAttemptService.isLocked(emailKey, now)) {
			throw loginLocked();
		}

		User user = userRepository.findByEmail(normalizeEmail(rawEmail)).orElse(null);
		boolean passwordMatches = passwordEncoder.matches(password,
				user != null ? user.getPasswordHash() : dummyPasswordHash);
		if (user == null || !passwordMatches) {
			boolean locked = loginAttemptService.recordFailure(emailKey, now);
			throw locked ? loginLocked() : new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}

		loginAttemptService.reset(emailKey);
		return issueTokens(user);
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public AuthResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
		return new AuthResult(rotated.user(), accessTokenProvider.issue(rotated.user()), rotated.refreshToken());
	}

	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			refreshTokenService.revokeFamilyOf(refreshToken, RevokedReason.LOGOUT);
		}
	}

	private AuthResult issueTokens(User user) {
		return new AuthResult(user, accessTokenProvider.issue(user), refreshTokenService.issueNewFamily(user));
	}

	/**
	 * 잠금 안내 문구의 빈칸을 실제 설정값으로 채운다 (FR-AUTH-02: 문구와 설정이 어긋나지 않게).
	 * {0}: 최대 실패 횟수(max-failures), {1}: 잠금 시간(lock-duration, 예: 10m → "10분")
	 */
	private BusinessException loginLocked() {
		AuthProperties.Login login = properties.login();
		return BusinessException.withArgs(AuthErrorCode.LOGIN_LOCKED, login.maxFailures(),
				KoreanDuration.format(login.lockDuration()));
	}

	/** 닉네임을 입력하지 않으면 이메일 앞부분을 기본 닉네임으로 쓴다. */
	private static String resolveNickname(String nickname, String email) {
		String value = (nickname == null || nickname.isBlank()) ? email.substring(0, email.indexOf('@')) : nickname.trim();
		return value.length() > NICKNAME_MAX_LENGTH ? value.substring(0, NICKNAME_MAX_LENGTH) : value;
	}

	public record SignupCommand(String email, String password, String nickname, boolean agreedTerms,
			boolean agreedPrivacy, boolean agreedSkinData) {
	}

	public record AuthResult(User user, String accessToken, String refreshToken) {
	}
}
