package com.aibeauty.api.product.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.global.error.BusinessException;
import com.aibeauty.api.global.error.CommonErrorCode;
import com.aibeauty.api.product.api.dto.ResCategoryDTO;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;
import com.aibeauty.api.product.api.dto.ResProductDetailDTO;
import com.aibeauty.api.product.domain.CategoryRepository;
import com.aibeauty.api.product.domain.IngredientStatus;
import com.aibeauty.api.product.domain.Product;
import com.aibeauty.api.product.domain.ProductIngredient;
import com.aibeauty.api.product.domain.ProductIngredientRepository;
import com.aibeauty.api.product.domain.ProductRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductIngredientRepository productIngredientRepository;
	private final CategoryRepository categoryRepository;
	private final ProductCardQuery productCardQuery;

	public ProductService(ProductRepository productRepository,
			ProductIngredientRepository productIngredientRepository, CategoryRepository categoryRepository,
			ProductCardQuery productCardQuery) {
		this.productRepository = productRepository;
		this.productIngredientRepository = productIngredientRepository;
		this.categoryRepository = categoryRepository;
		this.productCardQuery = productCardQuery;
	}

	public List<ResCategoryDTO> categories() {
		return categoryRepository.findAllByOrderByDisplayOrder().stream().map(ResCategoryDTO::from).toList();
	}

	/** @param categoryCode null이면 전체. 없는 코드면 입력 오류 */
	public ResPageDTO<ResProductCardDTO> list(String categoryCode, ProductSort sort, int page, int size) {
		Long categoryId = null;
		if (categoryCode != null && !categoryCode.isBlank()) {
			categoryId = categoryRepository.findByCode(categoryCode)
					.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT, "category=" + categoryCode))
					.getId();
		}
		return productCardQuery.find(ProductCardQuery.Condition.all().withCategory(categoryId), sort, page, size)
				.map(ResProductCardDTO::from);
	}

	/** 숨김 상품은 없는 상품과 같이 404로 응답한다 */
	public ResProductDetailDTO detail(long productId) {
		Product product = productRepository.findDetailById(productId)
				.filter(Product::isVisible)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "productId=" + productId));
		List<ProductIngredient> ingredients = productIngredientRepository.findAllByProductIdWithIngredient(productId);

		String sourceUrl = product.getSourceUrl() != null ? product.getSourceUrl() : product.getDataSource().getUrl();
		List<DataIssue> issues = dataIssues(product, ingredients);
		return new ResProductDetailDTO(
				product.getId(),
				product.getName(),
				new ResProductDetailDTO.Brand(product.getBrand().getId(), product.getBrand().getName()),
				new ResProductDetailDTO.Category(product.getCategory().getCode(), product.getCategory().getName()),
				product.getDescription(),
				product.getImageUrl(),
				product.getVolume(),
				product.getPrice(),
				product.getPriceUpdatedAt(),
				product.getSkinTypes().stream().sorted().toList(),
				product.getConcerns().stream()
						.map(concern -> new ResProductDetailDTO.Concern(concern.getCode(), concern.getName()))
						.toList(),
				ingredients.stream().map(ProductService::toIngredient).toList(),
				new ResProductDetailDTO.DataStatus(issues.isEmpty(), issues),
				new ResProductDetailDTO.Source(product.getDataSource().getName(), sourceUrl,
						product.getSourceCollectedAt()));
	}

	private static ResProductDetailDTO.Ingredient toIngredient(ProductIngredient line) {
		Long ingredientId = line.isMatched() ? line.getIngredient().getId() : null;
		String standardName = line.isMatched() ? line.getIngredient().getStandardName() : null;
		return new ResProductDetailDTO.Ingredient(line.getDisplayOrder(), line.getRawName(), ingredientId,
				standardName);
	}

	/** 목록 카드의 dataSufficient(ProductCardQuery)와 같은 기준이다 */
	static List<DataIssue> dataIssues(Product product, List<ProductIngredient> ingredients) {
		List<DataIssue> issues = new ArrayList<>();
		if (product.getIngredientStatus() != IngredientStatus.COMPLETE || ingredients.isEmpty()) {
			issues.add(DataIssue.INGREDIENTS_INCOMPLETE);
		}
		if (ingredients.stream().anyMatch(line -> !line.isMatched())) {
			issues.add(DataIssue.UNMATCHED_INGREDIENTS);
		}
		return issues;
	}
}
