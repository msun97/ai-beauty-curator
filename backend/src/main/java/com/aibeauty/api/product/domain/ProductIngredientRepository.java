package com.aibeauty.api.product.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, ProductIngredientId> {

	@Query("""
			SELECT pi FROM ProductIngredient pi LEFT JOIN FETCH pi.ingredient
			WHERE pi.product.id = :productId
			ORDER BY pi.displayOrder
			""")
	List<ProductIngredient> findAllByProductIdWithIngredient(Long productId);

	/** 이 성분이 들어간 노출 중인 상품 수 */
	@Query("""
			SELECT count(DISTINCT pi.product.id) FROM ProductIngredient pi
			WHERE pi.ingredient.id = :ingredientId AND pi.product.status = com.aibeauty.api.product.domain.ProductStatus.ACTIVE
			""")
	long countVisibleProductsByIngredientId(Long ingredientId);
}
