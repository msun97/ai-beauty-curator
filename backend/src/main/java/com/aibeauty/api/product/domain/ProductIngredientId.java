package com.aibeauty.api.product.domain;

import java.io.Serializable;
import java.util.Objects;

/** product_ingredients 복합 키 (product_id, display_order) */
public class ProductIngredientId implements Serializable {

	private Long product;
	private Integer displayOrder;

	protected ProductIngredientId() {
	}

	public ProductIngredientId(Long product, Integer displayOrder) {
		this.product = product;
		this.displayOrder = displayOrder;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ProductIngredientId other
				&& Objects.equals(product, other.product)
				&& Objects.equals(displayOrder, other.displayOrder);
	}

	@Override
	public int hashCode() {
		return Objects.hash(product, displayOrder);
	}
}
