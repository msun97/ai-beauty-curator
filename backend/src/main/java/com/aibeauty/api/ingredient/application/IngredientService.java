package com.aibeauty.api.ingredient.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.ingredient.api.dto.ResIngredientDetailDTO;
import com.aibeauty.api.ingredient.api.dto.ResIngredientLookupDTO;
import com.aibeauty.api.ingredient.api.dto.ResIngredientLookupDTO.MatchedBy;
import com.aibeauty.api.ingredient.domain.Ingredient;
import com.aibeauty.api.ingredient.domain.IngredientAliasRepository;
import com.aibeauty.api.ingredient.domain.IngredientNames;
import com.aibeauty.api.ingredient.domain.IngredientRepository;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;
import com.aibeauty.api.product.application.ProductCardQuery;
import com.aibeauty.api.product.application.ProductSort;
import com.aibeauty.api.product.domain.ProductIngredientRepository;

@Service
@Transactional(readOnly = true)
public class IngredientService {

	private final IngredientRepository ingredientRepository;
	private final IngredientAliasRepository aliasRepository;
	private final ProductIngredientRepository productIngredientRepository;
	private final ProductCardQuery productCardQuery;

	public IngredientService(IngredientRepository ingredientRepository, IngredientAliasRepository aliasRepository,
			ProductIngredientRepository productIngredientRepository, ProductCardQuery productCardQuery) {
		this.ingredientRepository = ingredientRepository;
		this.aliasRepository = aliasRepository;
		this.productIngredientRepository = productIngredientRepository;
		this.productCardQuery = productCardQuery;
	}

	public ResIngredientDetailDTO detail(long ingredientId) {
		Ingredient ingredient = find(ingredientId);
		String normalizedStandardName = IngredientNames.normalize(ingredient.getStandardName());
		List<String> aliases = aliasRepository.findByIngredientIdOrderById(ingredientId).stream()
				.filter(alias -> !alias.getNormalizedAlias().equals(normalizedStandardName))
				.map(alias -> alias.getAlias())
				.toList();
		return new ResIngredientDetailDTO(
				ingredient.getId(),
				ingredient.getStandardName(),
				ingredient.getInciName(),
				aliases,
				ingredient.getDescription(),
				new ResIngredientDetailDTO.Source(ingredient.getSourceName(), ingredient.getSourceUrl(),
						ingredient.getVerifiedAt()),
				productIngredientRepository.countVisibleProductsByIngredientId(ingredientId));
	}

	/** 이 성분이 들어간 상품 (FR-ING-01) */
	public ResPageDTO<ResProductCardDTO> products(long ingredientId, ProductSort sort, int page, int size) {
		find(ingredientId);
		return productCardQuery.find(ProductCardQuery.Condition.all().withIngredient(ingredientId), sort, page, size)
				.map(ResProductCardDTO::from);
	}

	/**
	 * 이름으로 표준 성분을 찾는다 (FR-ING-02). 표준명이 정확히 같으면 먼저 택하고,
	 * 아니면 띄어쓰기·기호·대소문자를 무시하고 별칭과 비교한다.
	 */
	public ResIngredientLookupDTO lookup(String name) {
		return findByName(name).orElseThrow(
				() -> new BusinessException(CommonErrorCode.NOT_FOUND, "ingredientName=" + IngredientNames.clean(name)));
	}

	/** {@link #lookup(String)}과 같은 규칙. 없으면 빈 값 */
	public Optional<ResIngredientLookupDTO> findByName(String name) {
		String trimmed = IngredientNames.clean(name);
		if (IngredientNames.normalize(trimmed).isEmpty()) {
			return Optional.empty();
		}
		return ingredientRepository.findByStandardName(trimmed)
				.map(ingredient -> new ResIngredientLookupDTO(ingredient.getId(), ingredient.getStandardName(),
						ingredient.getStandardName(), MatchedBy.STANDARD_NAME))
				.or(() -> aliasRepository.findWithIngredientByNormalizedAlias(IngredientNames.normalize(trimmed))
						.map(alias -> {
							Ingredient ingredient = alias.getIngredient();
							MatchedBy matchedBy = alias.getNormalizedAlias()
									.equals(IngredientNames.normalize(ingredient.getStandardName()))
											? MatchedBy.STANDARD_NAME
											: MatchedBy.ALIAS;
							String matchedName = matchedBy == MatchedBy.STANDARD_NAME
									? ingredient.getStandardName()
									: alias.getAlias();
							return new ResIngredientLookupDTO(ingredient.getId(), ingredient.getStandardName(),
									matchedName, matchedBy);
						}));
	}

	private Ingredient find(long ingredientId) {
		return ingredientRepository.findById(ingredientId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "ingredientId=" + ingredientId));
	}
}
