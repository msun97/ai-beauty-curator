package com.aibeauty.api.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.auth.api.dto.ResAuthUserDTO;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.user.domain.UserRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** 로그인한 사용자 정보. 토큰의 sub(사용자 ID)로 조회한다. */
	@GetMapping("/me")
	public ResAuthUserDTO me(@AuthenticationPrincipal Jwt jwt) {
		Long userId = Long.valueOf(jwt.getSubject());
		return userRepository.findById(userId)
			.map(ResAuthUserDTO::from)
			// 토큰은 유효하지만 탈퇴 등으로 계정이 없는 경우
			.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
	}
}
