package com.aibeauty.api.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 피부 고민. 기준 코드라 마이그레이션(V3)으로만 바뀐다. */
@Entity
@Table(name = "skin_concerns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinConcern {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int displayOrder;
}
