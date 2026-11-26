package com.aibeauty.api.product.api.dto;

import com.aibeauty.api.product.domain.Category;

public record ResCategoryDTO(String code, String name) {

	public static ResCategoryDTO from(Category category) {
		return new ResCategoryDTO(category.getCode(), category.getName());
	}
}
