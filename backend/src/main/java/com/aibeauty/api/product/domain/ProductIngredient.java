package com.aibeauty.api.product.domain;

import com.aibeauty.api.ingredient.domain.Ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 전성분 한 줄. 표시 순서가 곧 함량 순서다. 매칭에 실패하면 ingredient가 null이고 rawName만 있다. */
@Entity
@Table(name = "product_ingredients")
@IdClass(ProductIngredientId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductIngredient {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id")
	private Product product;

	@Id
	@Column(nullable = false)
	private Integer displayOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ingredient_id")
	private Ingredient ingredient;

	@Column(nullable = false)
	private String rawName;

	public boolean isMatched() {
		return ingredient != null;
	}
}
