package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Review;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"user", "user.profile"})
    Page<Review> findByTool_IdOrderByCreatedAtDescIdAsc(Long toolId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "user.profile"})
    Optional<Review> findByTool_IdAndId(Long toolId, Long reviewId);

    Optional<Review> findByTool_IdAndUser_Id(Long toolId, Long userId);

    @EntityGraph(attributePaths = {"user", "user.profile"})
    Page<Review> findByUser_IdOrderByCreatedAtDescIdAsc(Long userId, Pageable pageable);

    boolean existsByTool_IdAndUser_Id(Long toolId, Long userId);

    @Query("""
            SELECT r.tool.id AS toolId,
                   AVG(r.rating) AS avgRating,
                   COUNT(r.id) AS reviewCount
            FROM Review r
            WHERE r.tool.id IN :toolIds
            GROUP BY r.tool.id
            """)
    List<ReviewSummaryProjection> summarizeByToolIds(@Param("toolIds") Collection<Long> toolIds);
}
