package com.aibeauty.api.search.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aibeauty.api.product.api.ProductController;
import com.aibeauty.api.product.application.ProductSort;
import com.aibeauty.api.search.api.dto.ResAutocompleteDTO;
import com.aibeauty.api.search.api.dto.ResSearchDTO;
import com.aibeauty.api.search.application.SearchFilter;
import com.aibeauty.api.search.application.SearchService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 검색. 비회원도 쓸 수 있고, 기피 성분 제외 필터만 로그인이 필요하다. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

	static final int MAX_QUERY_LENGTH = 100;

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	/** FR-SRC-01~03 */
	@GetMapping
	public ResSearchDTO search(
			@RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String q,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Long brandId,
			@RequestParam(required = false) @Min(0) Integer minPrice,
			@RequestParam(required = false) @Min(0) Integer maxPrice,
			@RequestParam(defaultValue = "false") boolean excludeAvoided,
			@RequestParam(defaultValue = "POPULAR") ProductSort sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(ProductController.MAX_PAGE_SIZE) int size,
			@AuthenticationPrincipal Jwt jwt) {
		Long userId = jwt == null ? null : Long.valueOf(jwt.getSubject());
		SearchFilter filter = new SearchFilter(category, brandId, minPrice, maxPrice, excludeAvoided);
		return searchService.search(q, filter, sort, page, size, userId);
	}

	/** FR-SRC-04 */
	@GetMapping("/autocomplete")
	public ResAutocompleteDTO autocomplete(@RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String q) {
		return searchService.autocomplete(q);
	}
}
