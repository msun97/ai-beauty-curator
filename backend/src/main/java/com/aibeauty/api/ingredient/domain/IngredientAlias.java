package com.aibeauty.api.ingredient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 성분의 다른 이름. normalizedAlias는 {@link IngredientNames#normalize(String)} 결과다. */
@Entity
@Table(name = "ingredient_aliases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ingredient_id")
	private Ingredient ingredient;

	@Column(nullable = false)
	private String alias;

	@Column(nullable = false, unique = true)
	private String normalizedAlias;
}
