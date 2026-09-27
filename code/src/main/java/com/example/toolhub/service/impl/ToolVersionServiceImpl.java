package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.entity.ToolVersion;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.ToolVersionRequest;
import com.example.toolhub.dto.response.ToolVersionResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.repository.ToolVersionRepository;
import com.example.toolhub.security.CurrentActor;
import com.example.toolhub.service.ToolVersionService;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolVersionServiceImpl implements ToolVersionService {
    private final ToolRepository toolRepository;
    private final ToolVersionRepository versionRepository;

    public ToolVersionServiceImpl(ToolRepository toolRepository, ToolVersionRepository versionRepository) {
        this.toolRepository = toolRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolVersionResponse> list(Long toolId, CurrentActor actor) {
        Tool tool = findTool(toolId);
        boolean owner = actor != null && actor.id() != null && actor.id().equals(tool.getOwnerId());
        boolean admin = actor != null && actor.admin();
        if (tool.getStatus() != ToolStatus.PUBLISHED && !owner && !admin) {
            throw new ResourceNotFoundException("Tool not found: " + toolId);
        }
        return versionRepository.findByToolIdOrderByIdDesc(toolId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ToolVersionResponse get(Long toolId, Long versionId, CurrentActor actor) {
        Tool tool = findTool(toolId);
        boolean owner = actor != null && actor.id() != null && actor.id().equals(tool.getOwnerId());
        boolean admin = actor != null && actor.admin();
        if (tool.getStatus() != ToolStatus.PUBLISHED && !owner && !admin) {
            throw new ResourceNotFoundException("Tool not found: " + toolId);
        }
        return toResponse(findVersion(toolId, versionId));
    }

    @Override
    @Transactional
    public ToolVersionResponse create(Long toolId, ToolVersionRequest request, CurrentActor actor) {
        Tool tool = findToolForUpdate(toolId);
        requireDraftOwner(tool, actor);
        String version = normalizeVersion(request.version());
        if (versionRepository.existsByToolIdAndVersion(toolId, version)) {
            throw new CatalogConflictException("Tool version already exists");
        }
        return toResponse(versionRepository.save(new ToolVersion(tool, version, request.releaseNotes())));
    }

    @Override
    @Transactional
    public ToolVersionResponse update(Long toolId, Long versionId, ToolVersionRequest request, CurrentActor actor) {
        Tool tool = findToolForUpdate(toolId);
        requireDraftOwner(tool, actor);
        ToolVersion toolVersion = findVersion(toolId, versionId);
        String version = normalizeVersion(request.version());
        if (versionRepository.existsByToolIdAndVersionAndIdNot(toolId, version, versionId)) {
            throw new CatalogConflictException("Tool version already exists");
        }
        toolVersion.update(version, request.releaseNotes());
        return toResponse(toolVersion);
    }

    @Override
    @Transactional
    public void delete(Long toolId, Long versionId, CurrentActor actor) {
        Tool tool = findToolForUpdate(toolId);
        requireDraftOwner(tool, actor);
        versionRepository.delete(findVersion(toolId, versionId));
    }

    private Tool findTool(Long toolId) {
        return toolRepository.findById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));
    }

    private Tool findToolForUpdate(Long toolId) {
        return toolRepository.findForUpdateById(toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + toolId));
    }

    private ToolVersion findVersion(Long toolId, Long versionId) {
        return versionRepository.findByIdAndToolId(versionId, toolId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool version not found: " + versionId));
    }

    private void requireDraftOwner(Tool tool, CurrentActor actor) {
        if (actor == null || actor.id() == null || !actor.id().equals(tool.getOwnerId())) {
            throw new AccessDeniedException("You do not own this tool");
        }
        if (tool.getStatus() != ToolStatus.DRAFT) {
            throw new InvalidStateTransitionException("Versions can only be edited while the tool is a draft");
        }
    }

    private String normalizeVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Version must contain 1 to 100 characters");
        }
        return normalized;
    }

    private ToolVersionResponse toResponse(ToolVersion version) {
        return new ToolVersionResponse(version.getId(), version.getTool().getId(), version.getVersion(),
                version.getReleaseNotes(), version.getReleasedAt(), version.getCreatedAt(), version.getUpdatedAt());
    }
}
