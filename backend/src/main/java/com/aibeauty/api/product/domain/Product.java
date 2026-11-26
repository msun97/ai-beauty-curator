package com.aibeauty.api.product.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품. 전성분은 행 수가 많고 표시 순서가 중요해 {@link ProductIngredient}로 따로 조회한다.
 */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "brand_id")
	private Brand brand;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private Category category;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "data_source_id")
	private DataSource dataSource;

	@Column(nullable = false)
	private String name;

	private String description;

	private String imageUrl;

	private String volume;

	/** 현재가(원). 모르면 null */
	private Integer price;

	private Instant priceUpdatedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IngredientStatus ingredientStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductStatus status;

	/** 상품 정보를 확인한 페이지. 없으면 출처(data_sources)의 URL을 쓴다 */
	private String sourceUrl;

	@Column(nullable = false)
	private LocalDate sourceCollectedAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@ElementCollection
	@CollectionTable(name = "product_skin_types", joinColumns = @JoinColumn(name = "product_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "skin_type", nullable = false)
	private Set<SkinType> skinTypes = new LinkedHashSet<>();

	@ManyToMany
	@JoinTable(name = "product_concern_tags",
			joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "concern_id"))
	@OrderBy("displayOrder")
	private Set<SkinConcern> concerns = new LinkedHashSet<>();

	public boolean isVisible() {
		return status == ProductStatus.ACTIVE;
	}
}
