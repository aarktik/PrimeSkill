package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.ToolService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolServiceImpl implements ToolService {
    private final ToolRepository toolRepository;
    private final CategoryRepository categoryRepository;
    private final ToolMapper toolMapper;

    public ToolServiceImpl(ToolRepository toolRepository,
                           CategoryRepository categoryRepository,
                           ToolMapper toolMapper) {
        this.toolRepository = toolRepository;
        this.categoryRepository = categoryRepository;
        this.toolMapper = toolMapper;
    }

    @Override
    @Transactional
    public ToolResponse create(CreateToolRequest request, Long actorUserId) {
        requireActor(actorUserId);
        if (toolRepository.existsBySlug(request.slug())) {
            throw new CatalogConflictException("Tool slug already exists");
        }
        Category category = findCategory(request.categoryId());
        Tool tool = new Tool(actorUserId, category, request.name(), request.slug(),
                request.shortDescription(), request.description(), request.repositoryUrl());
        return toolMapper.toResponse(toolRepository.save(tool));
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResponse getByIdOrSlug(String idOrSlug, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = findByIdOrSlug(idOrSlug);
        boolean publicTool = tool.getStatus() == ToolStatus.PUBLISHED;
        boolean owner = actorUserId != null && actorUserId.equals(tool.getOwnerId());
        if (!publicTool && !owner && !actorIsAdmin) {
            throw new ResourceNotFoundException("Tool not found: " + idOrSlug);
        }
        return toolMapper.toResponse(tool);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ToolResponse> listOwnedBy(Long actorUserId, Pageable pageable) {
        requireActor(actorUserId);
        return toolRepository.findByOwnerId(actorUserId, pageable).map(toolMapper::toResponse);
    }

    @Override
    @Transactional
    public ToolResponse update(Long id, UpdateToolRequest request, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = findTool(id);
        assertCanMutate(tool, actorUserId, actorIsAdmin);
        if (toolRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new CatalogConflictException("Tool slug already exists");
        }
        Category category = findCategory(request.categoryId());
        tool.updateCategory(category);
        tool.updateDetails(request.name(), request.slug(), request.shortDescription(),
                request.description(), request.repositoryUrl());
        return toolMapper.toResponse(tool);
    }

    @Override
    @Transactional
    public void delete(Long id, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = findTool(id);
        assertCanMutate(tool, actorUserId, actorIsAdmin);
        toolRepository.delete(tool);
    }

    private Tool findTool(Long id) {
        return toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + id));
    }

    private Tool findByIdOrSlug(String idOrSlug) {
        Tool tool = toolRepository.findBySlug(idOrSlug).orElse(null);
        if (tool != null) {
            return tool;
        }
        try {
            return findTool(Long.valueOf(idOrSlug));
        } catch (NumberFormatException exception) {
            throw new ResourceNotFoundException("Tool not found: " + idOrSlug);
        }
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private void assertCanMutate(Tool tool, Long actorUserId, boolean actorIsAdmin) {
        if (!actorIsAdmin && (actorUserId == null || !actorUserId.equals(tool.getOwnerId()))) {
            throw new AccessDeniedException("You do not own this tool");
        }
    }

    private void requireActor(Long actorUserId) {
        if (actorUserId == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
    }
}
