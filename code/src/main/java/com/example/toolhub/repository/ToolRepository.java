package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    @EntityGraph(attributePaths = "category")
    Optional<Tool> findBySlug(String slug);

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = "category")
    Page<Tool> findByOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Tool> findByStatus(ToolStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tool t SET t.viewCount = t.viewCount + 1 "
            + "WHERE t.id = :toolId AND t.status = :status")
    int incrementPublishedViewCount(@Param("toolId") Long toolId,
                                    @Param("status") ToolStatus status);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Tool t SET t.viewCount = t.viewCount + 1 "
            + "WHERE t.id = :toolId AND t.status = :status AND t.ownerId <> :actorUserId")
    int incrementPublishedViewCountExcludingOwner(@Param("toolId") Long toolId,
                                                  @Param("status") ToolStatus status,
                                                  @Param("actorUserId") Long actorUserId);

    @EntityGraph(attributePaths = "category")
    @Query(
            value = "SELECT DISTINCT t FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')",
            countQuery = "SELECT COUNT(DISTINCT t.id) FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')")
    Page<Tool> searchPublished(
            @Param("status") ToolStatus status,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query(
            value = "SELECT DISTINCT t FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
                    + "AND EXISTS (SELECT 1 FROM ToolTag tt WHERE tt.tool.id = t.id AND tt.tag.slug IN :tagSlugs)",
            countQuery = "SELECT COUNT(DISTINCT t.id) FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (:keyword IS NULL "
                    + "OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
                    + "AND EXISTS (SELECT 1 FROM ToolTag tt WHERE tt.tool.id = t.id AND tt.tag.slug IN :tagSlugs)")
    Page<Tool> searchPublishedWithTags(
            @Param("status") ToolStatus status,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("tagSlugs") List<String> tagSlugs,
            Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query(
            value = "SELECT DISTINCT t FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
                    + "ORDER BY CASE WHEN LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' THEN 0 "
                    + "WHEN LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' THEN 1 "
                    + "ELSE 2 END, t.createdAt DESC, t.id ASC",
            countQuery = "SELECT COUNT(DISTINCT t.id) FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')")
    Page<Tool> searchPublishedByRelevance(
            @Param("status") ToolStatus status,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query(
            value = "SELECT DISTINCT t FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
                    + "AND EXISTS (SELECT 1 FROM ToolTag tt WHERE tt.tool.id = t.id AND tt.tag.slug IN :tagSlugs) "
                    + "ORDER BY CASE WHEN LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' THEN 0 "
                    + "WHEN LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' THEN 1 "
                    + "ELSE 2 END, t.createdAt DESC, t.id ASC",
            countQuery = "SELECT COUNT(DISTINCT t.id) FROM Tool t "
                    + "WHERE t.status = :status "
                    + "AND (:categoryId IS NULL OR t.category.id = :categoryId) "
                    + "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\') "
                    + "AND EXISTS (SELECT 1 FROM ToolTag tt WHERE tt.tool.id = t.id AND tt.tag.slug IN :tagSlugs)")
    Page<Tool> searchPublishedWithTagsByRelevance(
            @Param("status") ToolStatus status,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("tagSlugs") List<String> tagSlugs,
            Pageable pageable);
}
