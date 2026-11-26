package com.aibeauty.api.product.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품·리뷰 데이터의 출처 */
@Entity
@Table(name = "data_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(nullable = false)
	private String sourceType;

	private String url;

	private String usageTerms;

	private LocalDate collectedAt;
}
