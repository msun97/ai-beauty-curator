package com.aibeauty.api.product.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	List<Category> findAllByOrderByDisplayOrder();

	Optional<Category> findByCode(String code);
}
