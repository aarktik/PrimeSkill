package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlug(String slug);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsBySlugAndIdNot(String slug, Long id);
}
