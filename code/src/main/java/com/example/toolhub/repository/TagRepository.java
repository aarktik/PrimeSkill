package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.Tag;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tag FROM Tag tag WHERE tag.id = :id")
    Optional<Tag> findForUpdateById(@Param("id") Long id);

    Optional<Tag> findBySlug(String slug);

    List<Tag> findBySlugIn(List<String> slugs);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
