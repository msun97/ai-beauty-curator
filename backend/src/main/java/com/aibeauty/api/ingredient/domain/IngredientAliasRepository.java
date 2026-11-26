package com.aibeauty.api.ingredient.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long> {

	@Query("SELECT a FROM IngredientAlias a JOIN FETCH a.ingredient WHERE a.normalizedAlias = :normalizedAlias")
	Optional<IngredientAlias> findWithIngredientByNormalizedAlias(String normalizedAlias);

	List<IngredientAlias> findByIngredientIdOrderById(Long ingredientId);
}
