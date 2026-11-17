package com.aibeauty.api.auth.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.auth.domain.PasswordResetToken;
import com.aibeauty.api.auth.domain.PasswordResetTokenRepository;
import com.aibeauty.api.auth.domain.RevokedReason;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.ErrorCode;
import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.global.security.TokenGenerator;
import com.aibeauty.api.user.domain.User;
import com.aibeauty.api.user.domain.UserRepository;

/**
 * 비밀번호 재설정 (FR-AUTH-05).
 */
@Service
public class PasswordResetService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final RefreshTokenService refreshTokenService;
	private final PasswordResetMailSender mailSender;
	private final PasswordEncoder passwordEncoder;
	private final AuthProperties properties;
	private final Clock clock;

	public PasswordResetService(UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository, RefreshTokenService refreshTokenService,
			PasswordResetMailSender mailSender, PasswordEncoder passwordEncoder, AuthProperties properties,
			Clock clock) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.refreshTokenService = refreshTokenService;
		this.mailSender = mailSender;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 가입된 이메일이면 재설정 링크를 보낸다. 가입 여부를 알 수 없도록,
	 * 호출하는 쪽은 결과와 관계없이 같은 응답을 반환한다.
	 */
	@Transactional
	public void request(String rawEmail) {
		userRepository.findByEmail(AuthService.normalizeEmail(rawEmail)).ifPresent(user -> {
			Instant now = clock.instant();
			passwordResetTokenRepository.invalidateAllByUser(user.getId(), now);

			String rawToken = TokenGenerator.newToken();
			passwordResetTokenRepository.save(PasswordResetToken.issue(user, TokenGenerator.hash(rawToken),
					now.plus(properties.passwordReset().tokenTtl()), now));
			mailSender.send(user.getEmail(), properties.passwordReset().linkBaseUrl() + "?token=" + rawToken);
		});
	}

	/** 새 비밀번호로 바꾸고, 기존 로그인 상태(Refresh Token)를 모두 해제한다. */
	@Transactional
	public void confirm(String rawToken, String newPassword) {
		Instant now = clock.instant();
		PasswordResetToken token = passwordResetTokenRepository
			.findByTokenHashForUpdate(TokenGenerator.hash(rawToken))
			.filter(t -> t.isUsable(now))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));

		token.markUsed(now);
		User user = token.getUser();
		user.changePassword(passwordEncoder.encode(newPassword), now);
		refreshTokenService.revokeAll(user.getId(), RevokedReason.PASSWORD_RESET);
	}
}
