package com.aibeauty.api.product.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Query("""
			SELECT p FROM Product p
			JOIN FETCH p.brand JOIN FETCH p.category JOIN FETCH p.dataSource
			WHERE p.id = :id
			""")
	Optional<Product> findDetailById(Long id);
}
