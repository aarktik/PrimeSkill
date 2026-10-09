package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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
}
