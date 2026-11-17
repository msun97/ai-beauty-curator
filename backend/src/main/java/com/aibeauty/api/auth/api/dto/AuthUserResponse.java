package com.aibeauty.api.auth.api.dto;

import com.aibeauty.api.user.domain.User;

public record AuthUserResponse(Long id, String email, String nickname, String role) {

	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name());
	}
}
