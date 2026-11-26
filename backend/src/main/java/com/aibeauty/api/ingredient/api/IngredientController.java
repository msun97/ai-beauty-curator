package com.aibeauty.api.ingredient.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.ingredient.api.dto.ResIngredientDetailDTO;
import com.aibeauty.api.ingredient.api.dto.ResIngredientLookupDTO;
import com.aibeauty.api.ingredient.application.IngredientService;
import com.aibeauty.api.product.api.ProductController;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;
import com.aibeauty.api.product.application.ProductSort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 성분 조회. 비회원도 쓸 수 있다. */
@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

	private final IngredientService ingredientService;

	public IngredientController(IngredientService ingredientService) {
		this.ingredientService = ingredientService;
	}

	/** FR-ING-02. 이름(표준명 또는 별칭)으로 표준 성분 찾기. 없으면 404 */
	@GetMapping("/lookup")
	public ResIngredientLookupDTO lookup(@RequestParam @NotBlank @Size(max = 300) String name) {
		return ingredientService.lookup(name);
	}

	/** FR-ING-01 */
	@GetMapping("/{ingredientId}")
	public ResIngredientDetailDTO ingredient(@PathVariable long ingredientId) {
		return ingredientService.detail(ingredientId);
	}

	/** FR-ING-01. 이 성분이 들어간 상품 */
	@GetMapping("/{ingredientId}/products")
	public ResPageDTO<ResProductCardDTO> products(
			@PathVariable long ingredientId,
			@RequestParam(defaultValue = "POPULAR") ProductSort sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductController.MAX_PAGE_SIZE) int size) {
		return ingredientService.products(ingredientId, sort, page, size);
	}
}
