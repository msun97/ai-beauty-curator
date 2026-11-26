package com.aibeauty.api.product.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.global.api.ResPageDTO;
import com.aibeauty.api.product.api.dto.ResCategoryDTO;
import com.aibeauty.api.product.api.dto.ResProductCardDTO;
import com.aibeauty.api.product.api.dto.ResProductDetailDTO;
import com.aibeauty.api.product.application.ProductService;
import com.aibeauty.api.product.application.ProductSort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 상품 조회. 비회원도 쓸 수 있다. */
@RestController
@RequestMapping("/api")
public class ProductController {

	public static final int MAX_PAGE_SIZE = 50;

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping("/categories")
	public List<ResCategoryDTO> categories() {
		return productService.categories();
	}

	/** FR-PRD-01. category를 빼면 전체 카테고리 */
	@GetMapping("/products")
	public ResPageDTO<ResProductCardDTO> products(
			@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "POPULAR") ProductSort sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return productService.list(category, sort, page, size);
	}

	/** FR-PRD-02, FR-PRD-03 */
	@GetMapping("/products/{productId}")
	public ResProductDetailDTO product(@PathVariable long productId) {
		return productService.detail(productId);
	}
}
