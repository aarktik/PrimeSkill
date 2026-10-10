package com.example.toolhub.repository;

import com.example.toolhub.domain.entity.ToolVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolVersionRepository extends JpaRepository<ToolVersion, Long> {
    List<ToolVersion> findByToolIdOrderByIdDesc(Long toolId);

    Optional<ToolVersion> findByIdAndToolId(Long id, Long toolId);

    boolean existsByToolIdAndVersion(Long toolId, String version);

    boolean existsByToolIdAndVersionAndIdNot(Long toolId, String version, Long id);
}
