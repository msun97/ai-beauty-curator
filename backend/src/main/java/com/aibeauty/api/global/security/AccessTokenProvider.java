package com.aibeauty.api.global.security;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.aibeauty.api.user.domain.User;

/**
 * Access Token(JWT) 발급. 검증은 Spring Security OAuth2 Resource Server가 담당한다.
 */
@Component
public class AccessTokenProvider {

	public static final String ROLE_CLAIM = "role";

	private final JwtEncoder jwtEncoder;
	private final AuthProperties properties;
	private final Clock clock;

	public AccessTokenProvider(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public String issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.jwt().issuer())
			.subject(String.valueOf(user.getId()))
			.claim(ROLE_CLAIM, user.getRole().name())
			.issuedAt(now)
			.expiresAt(now.plus(properties.jwt().accessTokenTtl()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
