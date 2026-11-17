package com.aibeauty.api.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.auth.api.dto.AuthUserResponse;
import com.aibeauty.api.auth.api.dto.EmailAvailabilityResponse;
import com.aibeauty.api.auth.api.dto.LoginRequest;
import com.aibeauty.api.auth.api.dto.PasswordResetConfirmRequest;
import com.aibeauty.api.auth.api.dto.PasswordResetRequest;
import com.aibeauty.api.auth.api.dto.SignupRequest;
import com.aibeauty.api.auth.application.AuthService;
import com.aibeauty.api.auth.application.AuthService.AuthResult;
import com.aibeauty.api.auth.application.PasswordResetService;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.security.AuthCookies;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원가입·로그인·토큰 재발급·로그아웃·비밀번호 재설정.
 * 토큰은 응답 본문이 아닌 HttpOnly 쿠키로만 내려준다 (ADR-004).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final PasswordResetService passwordResetService;
	private final AuthCookies authCookies;

	public AuthController(AuthService authService, PasswordResetService passwordResetService,
			AuthCookies authCookies) {
		this.authService = authService;
		this.passwordResetService = passwordResetService;
		this.authCookies = authCookies;
	}

	@GetMapping("/email-availability")
	public EmailAvailabilityResponse emailAvailability(
			@RequestParam @NotBlank @Email(message = "이메일 형식이 올바르지 않아요.") String email) {
		return new EmailAvailabilityResponse(authService.isEmailAvailable(email));
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthUserResponse> signup(@Valid @RequestBody SignupRequest request,
			HttpServletResponse response) {
		AuthResult result = authService.signup(request.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(withCookies(result, response));
	}

	@PostMapping("/login")
	public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		return withCookies(authService.login(request.email(), request.password()), response);
	}

	@PostMapping("/refresh")
	public AuthUserResponse refresh(
			@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
			HttpServletResponse response) {
		try {
			return withCookies(authService.refresh(refreshToken), response);
		}
		catch (BusinessException e) {
			// 재발급에 실패하면 남아 있는 토큰 쿠키를 지워 다시 로그인하게 한다
			authCookies.clear(response);
			throw e;
		}
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
			HttpServletResponse response) {
		authService.logout(refreshToken);
		authCookies.clear(response);
	}

	/** 가입 여부와 관계없이 항상 같은 응답을 반환한다 (FR-AUTH-05). */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		passwordResetService.request(request.email());
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request,
			HttpServletResponse response) {
		passwordResetService.confirm(request.token(), request.newPassword());
		authCookies.clear(response);
	}

	private AuthUserResponse withCookies(AuthResult result, HttpServletResponse response) {
		authCookies.write(response, result.accessToken(), result.refreshToken());
		return AuthUserResponse.from(result.user());
	}
}
