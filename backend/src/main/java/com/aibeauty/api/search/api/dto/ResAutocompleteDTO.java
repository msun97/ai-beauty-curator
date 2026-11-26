package com.aibeauty.api.search.api.dto;

import java.util.List;

/** 입력 중 자동완성 (FR-SRC-04). 종류마다 최대 5개 */
public record ResAutocompleteDTO(List<Product> products, List<Brand> brands, List<Ingredient> ingredients) {

	public record Product(long id, String name, String brandName) {
	}

	public record Brand(long id, String name) {
	}

	public record Ingredient(long id, String standardName) {
	}
}
