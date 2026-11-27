package com.aibeauty.api.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.auth.api.dto.ReqEmailVerificationConfirmDTO;
import com.aibeauty.api.auth.api.dto.ReqEmailVerificationRequestDTO;
import com.aibeauty.api.auth.api.dto.ResAuthUserDTO;
import com.aibeauty.api.auth.api.dto.ResEmailVerificationDTO;
import com.aibeauty.api.auth.api.dto.ReqLoginDTO;
import com.aibeauty.api.auth.api.dto.ReqPasswordResetConfirmDTO;
import com.aibeauty.api.auth.api.dto.ReqPasswordResetDTO;
import com.aibeauty.api.auth.api.dto.ReqSignupDTO;
import com.aibeauty.api.auth.application.AuthService;
import com.aibeauty.api.auth.application.AuthService.AuthResult;
import com.aibeauty.api.auth.application.EmailVerificationService;
import com.aibeauty.api.auth.application.PasswordResetService;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.security.AuthCookies;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 이메일 인증·회원가입·로그인·토큰 재발급·로그아웃·비밀번호 재설정.
 * 가입 여부는 인증 코드를 맞힌 사람에게만 알려준다 (이메일 중복 확인 API 없음, FIX-008).
 * 토큰은 응답 본문이 아닌 HttpOnly 쿠키로만 내려준다 (ADR-004).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final PasswordResetService passwordResetService;
	private final EmailVerificationService emailVerificationService;
	private final AuthCookies authCookies;

	public AuthController(AuthService authService, PasswordResetService passwordResetService,
			EmailVerificationService emailVerificationService, AuthCookies authCookies) {
		this.authService = authService;
		this.passwordResetService = passwordResetService;
		this.emailVerificationService = emailVerificationService;
		this.authCookies = authCookies;
	}

	/** 가입 인증 코드 발송. 가입 여부와 관계없이 항상 같은 응답(202)을 반환한다 (FIX-008). */
	@PostMapping("/email-verification/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void requestEmailVerification(@Valid @RequestBody ReqEmailVerificationRequestDTO request) {
		emailVerificationService.requestCode(request.email());
	}

	/** 인증 코드 확인. 코드를 맞힌 사람에게만 가입 여부를 알려준다 (FIX-008). */
	@PostMapping("/email-verification/confirm")
	public ResEmailVerificationDTO confirmEmailVerification(@Valid @RequestBody ReqEmailVerificationConfirmDTO request) {
		return ResEmailVerificationDTO.from(emailVerificationService.confirmCode(request.email(), request.code()));
	}

	@PostMapping("/signup")
	public ResponseEntity<ResAuthUserDTO> signup(@Valid @RequestBody ReqSignupDTO reqSignupDTO,
			HttpServletResponse httpServletResponse) {
		AuthResult result = authService.signup(reqSignupDTO.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(withCookies(result, httpServletResponse));
	}

	@PostMapping("/login")
	public ResAuthUserDTO login(@Valid @RequestBody ReqLoginDTO request, HttpServletResponse response) {
		return withCookies(authService.login(request.email(), request.password()), response);
	}

	@PostMapping("/refresh")
	public ResAuthUserDTO refresh(
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
	public void requestPasswordReset(@Valid @RequestBody ReqPasswordResetDTO request) {
		passwordResetService.request(request.email());
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmPasswordReset(@Valid @RequestBody ReqPasswordResetConfirmDTO request,
			HttpServletResponse response) {
		passwordResetService.confirm(request.token(), request.newPassword());
		authCookies.clear(response);
	}

	private ResAuthUserDTO withCookies(AuthResult result, HttpServletResponse response) {
		authCookies.write(response, result.accessToken(), result.refreshToken());
		return ResAuthUserDTO.from(result.user());
	}
}
