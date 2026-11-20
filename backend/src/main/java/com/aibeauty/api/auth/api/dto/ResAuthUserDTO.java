package com.aibeauty.api.auth.api.dto;

import com.aibeauty.api.user.domain.User;

public record ResAuthUserDTO(Long id, String email, String nickname, String role) {

	public static ResAuthUserDTO from(User user) {
		return new ResAuthUserDTO(user.getId(), user.getEmail(), user.getNickname(), user.getRole().name());
	}
}
