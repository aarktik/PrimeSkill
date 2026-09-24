package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.ToolTag;
import com.example.toolhub.domain.entity.ToolTagId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolTagRepository extends JpaRepository<ToolTag, ToolTagId> {
    boolean existsByIdToolIdAndIdTagId(Long toolId, Long tagId);

    List<ToolTag> findByIdToolId(Long toolId);

    long countByIdTagId(Long tagId);

    void deleteByIdToolIdAndIdTagId(Long toolId, Long tagId);
}
