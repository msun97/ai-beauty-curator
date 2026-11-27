package com.aibeauty.api.global.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.aibeauty.api.global.ratelimit.ClientIpResolver;
import com.aibeauty.api.global.ratelimit.RateLimitFilter;
import com.aibeauty.api.global.ratelimit.RateLimitService;
import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.global.error.ErrorResponseWriter;

@Configuration
@EnableConfigurationProperties({ AuthProperties.class, EmailVerificationProperties.class })
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, ErrorResponseWriter errorResponseWriter,
			RateLimitService rateLimitService, ClientIpResolver clientIpResolver) throws Exception {
		CsrfHeaderFilter csrfHeaderFilter = new CsrfHeaderFilter(errorResponseWriter);
		http
			// 세션을 만들지 않고 매 요청 JWT로 인증한다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// 쿠키 기반 CSRF 대응은 CsrfHeaderFilter + SameSite=Lax로 처리한다
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				// 비회원 조회 API(트렌드·상품·성분·검색)는 각 기능 구현 시 여기에 permitAll로 추가한다
				.requestMatchers(HttpMethod.GET, "/api/categories", "/api/brands", "/api/products", "/api/products/*",
						"/api/ingredients/*", "/api/ingredients/*/products", "/api/search", "/api/search/autocomplete")
					.permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2
				.bearerTokenResolver(new CookieBearerTokenResolver())
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint((request, response, e) ->
					errorResponseWriter.write(response, CommonErrorCode.UNAUTHORIZED))
				.accessDeniedHandler((request, response, e) ->
					errorResponseWriter.write(response, CommonErrorCode.FORBIDDEN)))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, e) ->
					errorResponseWriter.write(response, CommonErrorCode.UNAUTHORIZED))
				.accessDeniedHandler((request, response, e) ->
					errorResponseWriter.write(response, CommonErrorCode.FORBIDDEN)))
			.addFilterBefore(csrfHeaderFilter, BearerTokenAuthenticationFilter.class)
			// 인증 API의 IP 기준 요청 횟수 제한 (FIX-009). CSRF 헤더가 없는 요청은 세지 않는다
			.addFilterAfter(new RateLimitFilter(rateLimitService, clientIpResolver, errorResponseWriter),
					CsrfHeaderFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		// 기본 BCrypt. 해시 앞에 {bcrypt} 접두사가 붙어 이후 알고리즘 교체가 가능하다
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	public JwtEncoder jwtEncoder(AuthProperties properties) {
		return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	public JwtDecoder jwtDecoder(AuthProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// 서명·만료 시각과 함께 발급자(iss)도 검증한다
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(AccessTokenProvider.ROLE_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	private static SecretKey secretKey(AuthProperties properties) {
		return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
