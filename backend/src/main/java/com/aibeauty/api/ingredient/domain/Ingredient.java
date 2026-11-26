package com.aibeauty.api.ingredient.domain;

import java.time.Instant;
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

/** 표준 성분. 데이터는 적재 작업(ingredient.importer)과 관리자 기능으로만 바뀐다. */
@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String standardName;

	private String inciName;

	/** 일반적 특성. 위험도 판정이 아니다 (FR-ING-03) */
	private String description;

	private String sourceName;

	private String sourceUrl;

	private LocalDate verifiedAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;
}
