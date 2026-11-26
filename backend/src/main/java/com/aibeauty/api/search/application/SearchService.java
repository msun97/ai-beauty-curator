package com.aibeauty.api.search.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.ingredient.api.dto.ResIngredientLookupDTO;
import com.aibeauty.api.ingredient.application.IngredientService;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;
import com.aibeauty.api.product.application.ProductCardQuery;
import com.aibeauty.api.product.application.ProductSort;
import com.aibeauty.api.product.domain.CategoryRepository;
import com.aibeauty.api.search.api.dto.ResAutocompleteDTO;
import com.aibeauty.api.search.api.dto.ResSearchDTO;

@Service
@Transactional(readOnly = true)
public class SearchService {

	static final int INGREDIENT_LIMIT = 10;
	static final int SUGGESTION_LIMIT = 6;
	static final int AUTOCOMPLETE_LIMIT = 5;

	private final ProductCardQuery productCardQuery;
	private final SearchQuery searchQuery;
	private final IngredientService ingredientService;
	private final CategoryRepository categoryRepository;

	public SearchService(ProductCardQuery productCardQuery, SearchQuery searchQuery,
			IngredientService ingredientService, CategoryRepository categoryRepository) {
		this.productCardQuery = productCardQuery;
		this.searchQuery = searchQuery;
		this.ingredientService = ingredientService;
		this.categoryRepository = categoryRepository;
	}

	/**
	 * 상품명·브랜드명 부분 일치 상품과, 검색어가 정확히 가리키는 성분이 든 상품을 함께 찾는다.
	 * 성분 목록은 별칭까지 부분 일치로 찾는다.
	 *
	 * @param userId 로그인 사용자. 비회원이면 null
	 */
	public ResSearchDTO search(String query, SearchFilter filter, ProductSort sort, int page, int size, Long userId) {
		String keyword = query.strip();
		ProductCardQuery.Condition condition = toCondition(filter, userId);

		Long exactIngredientId = ingredientService.findByName(keyword).map(ResIngredientLookupDTO::id).orElse(null);
		ResPageDTO<ResProductCardDTO> products = productCardQuery
				.find(condition.withKeyword(keyword, exactIngredientId), sort, page, size)
				.map(ResProductCardDTO::from);

		List<ResSearchDTO.Ingredient> ingredients = page == 0
				? searchQuery.ingredients(keyword, exactIngredientId, INGREDIENT_LIMIT).stream()
						.map(hit -> new ResSearchDTO.Ingredient(hit.id(), hit.standardName(), hit.matchedAlias(),
								hit.productCount()))
						.toList()
				: List.of();

		// FR-SRC-03: 결과가 없으면 필터와 관계없이 인기 상품을 제안한다
		List<ResProductCardDTO> suggestions = page == 0 && products.items().isEmpty() && ingredients.isEmpty()
				? productCardQuery.find(ProductCardQuery.Condition.all(), ProductSort.POPULAR, 0, SUGGESTION_LIMIT)
						.items().stream().map(ResProductCardDTO::from).toList()
				: List.of();

		return new ResSearchDTO(keyword, products, ingredients, suggestions);
	}

	public ResAutocompleteDTO autocomplete(String query) {
		String keyword = query.strip();
		return new ResAutocompleteDTO(
				searchQuery.productSuggestions(keyword, AUTOCOMPLETE_LIMIT).stream()
						.map(p -> new ResAutocompleteDTO.Product(p.id(), p.name(), p.brandName()))
						.toList(),
				searchQuery.brandSuggestions(keyword, AUTOCOMPLETE_LIMIT).stream()
						.map(b -> new ResAutocompleteDTO.Brand(b.id(), b.name()))
						.toList(),
				searchQuery.ingredientSuggestions(keyword, AUTOCOMPLETE_LIMIT).stream()
						.map(i -> new ResAutocompleteDTO.Ingredient(i.id(), i.standardName()))
						.toList());
	}

	private ProductCardQuery.Condition toCondition(SearchFilter filter, Long userId) {
		if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice() > filter.maxPrice()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
					"minPrice=" + filter.minPrice() + " > maxPrice=" + filter.maxPrice());
		}
		if (filter.excludeAvoided() && userId == null) {
			// 기피 성분 제외는 회원 기능이다 (FR-SRC-02)
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "excludeAvoided without login");
		}
		Long categoryId = null;
		if (filter.categoryCode() != null && !filter.categoryCode().isBlank()) {
			categoryId = categoryRepository.findByCode(filter.categoryCode())
					.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT,
							"category=" + filter.categoryCode()))
					.getId();
		}
		return ProductCardQuery.Condition.all()
				.withCategory(categoryId)
				.withBrand(filter.brandId())
				.withPriceRange(filter.minPrice(), filter.maxPrice())
				.excludingAvoidedOf(filter.excludeAvoided() ? userId : null);
	}
}
