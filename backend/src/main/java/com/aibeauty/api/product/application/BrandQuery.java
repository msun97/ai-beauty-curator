package com.aibeauty.api.product.application;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aibeauty.api.product.api.dto.ResBrandDTO;

@Repository
public class BrandQuery {

	private final JdbcTemplate jdbc;

	public BrandQuery(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 노출 중인 상품이 있는 브랜드를 이름순으로 */
	public List<ResBrandDTO> visibleBrands() {
		return jdbc.query("""
				SELECT b.id, b.name, count(p.id) AS product_count
				FROM brands b JOIN products p ON p.brand_id = b.id AND p.status = 'ACTIVE'
				GROUP BY b.id, b.name
				ORDER BY b.name
				""", (rs, rowNum) -> new ResBrandDTO(rs.getLong("id"), rs.getString("name"),
				rs.getLong("product_count")));
	}
}
