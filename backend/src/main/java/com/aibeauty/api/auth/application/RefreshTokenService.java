package com.aibeauty.api.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.auth.domain.RefreshToken;
import com.aibeauty.api.auth.domain.RefreshTokenRepository;
import com.aibeauty.api.auth.domain.RevokedReason;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.auth.domain.AuthErrorCode;
import com.aibeauty.api.global.security.AuthProperties;
import com.aibeauty.api.global.security.TokenGenerator;
import com.aibeauty.api.user.domain.User;

/**
 * Refresh Token 발급·회전·폐기 (ADR-004).
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthProperties properties;
	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthProperties properties, Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** 새 로그인: 새 토큰 묶음(family)을 시작한다. */
	@Transactional
	public String issueNewFamily(User user) {
		return issue(user, UUID.randomUUID());
	}

	/**
	 * 토큰 회전: 사용한 토큰은 사용 처리하고 같은 묶음으로 새 토큰을 발급한다.
	 * 이미 사용된 토큰이 다시 들어오면 탈취로 보고 묶음 전체를 폐기한다.
	 * 단, 사용된 지 유예 시간(refresh-reuse-grace) 이내면 화면 미리 불러오기·여러 탭에서 겹친
	 * 동시 요청으로 보고 같은 묶음으로 새 토큰을 발급한다 (FIX-003).
	 * 동시 요청은 행 잠금(FOR UPDATE)으로 순서대로 처리되므로, 뒤 요청은 앞 요청이 남긴 사용 시각을 본다.
	 * 폐기 결과는 예외를 던져도 저장되어야 하므로 BusinessException에는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public Rotated rotate(String rawToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(TokenGenerator.hash(rawToken))
			.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

		if (token.isRevoked() || token.isExpired(now)) {
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (token.isUsed()) {
			if (token.isUsedWithin(properties.jwt().refreshReuseGrace(), now)) {
				// 동시 요청: 묶음을 폐기하지 않고 같은 묶음으로 새 토큰 발급
				log.debug("Refresh Token 동시 재발급(유예 시간 이내): familyId={}", token.getFamilyId());
				User user = token.getUser();
				return new Rotated(user, issue(user, token.getFamilyId()));
			}
			refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokedReason.REUSE_DETECTED, now);
			log.warn("Refresh Token 재사용 감지: userId={}, familyId={}", token.getUser().getId(), token.getFamilyId());
			throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}

		token.markUsed(now);
		User user = token.getUser();
		return new Rotated(user, issue(user, token.getFamilyId()));
	}

	/** 로그아웃: 전달된 토큰이 속한 묶음을 폐기한다. 이미 무효한 토큰이어도 오류를 내지 않는다. */
	@Transactional
	public void revokeFamilyOf(String rawToken, RevokedReason reason) {
		refreshTokenRepository.findByTokenHash(TokenGenerator.hash(rawToken))
			.ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), reason, clock.instant()));
	}

	/** 비밀번호 재설정 등: 사용자의 모든 로그인 상태를 해제한다. */
	@Transactional
	public void revokeAll(Long userId, RevokedReason reason) {
		refreshTokenRepository.revokeAllByUser(userId, reason, clock.instant());
	}

	private String issue(User user, UUID familyId) {
		Instant now = clock.instant();
		String rawToken = TokenGenerator.newToken();
		refreshTokenRepository.save(RefreshToken.issue(user, TokenGenerator.hash(rawToken), familyId,
				now.plus(properties.jwt().refreshTokenTtl()), now));
		return rawToken;
	}

	public record Rotated(User user, String refreshToken) {
	}
}
