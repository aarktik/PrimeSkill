package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tool t where t.id = :id")
    Optional<Tool> findForUpdateById(@Param("id") Long id);

    @EntityGraph(attributePaths = "category")
    Optional<Tool> findBySlug(String slug);

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = "category")
    Page<Tool> findByOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Tool> findByStatus(ToolStatus status, Pageable pageable);
}
