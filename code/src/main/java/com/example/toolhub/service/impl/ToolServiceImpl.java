package com.example.toolhub.service.impl;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.entity.Tool;
import com.example.toolhub.domain.enums.ToolStatus;
import com.example.toolhub.dto.request.CreateToolRequest;
import com.example.toolhub.dto.request.UpdateToolRequest;
import com.example.toolhub.dto.response.ToolResponse;
import com.example.toolhub.exception.CatalogConflictException;
import com.example.toolhub.exception.InvalidStateTransitionException;
import com.example.toolhub.exception.ResourceNotFoundException;
import com.example.toolhub.mapper.ToolMapper;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.ToolRepository;
import com.example.toolhub.service.ToolService;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    public ToolServiceImpl(ToolRepository toolRepository,
                           CategoryRepository categoryRepository,
                           ToolMapper toolMapper, EntityManager entityManager) {
        this.toolRepository = toolRepository;
        this.categoryRepository = categoryRepository;
        this.toolMapper = toolMapper;
        this.entityManager = entityManager;
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
    public ToolResponse getById(Long id, Long actorUserId, boolean actorIsAdmin) {
        return toolMapper.toResponse(requireVisible(findTool(id), actorUserId, actorIsAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public ToolResponse getByIdOrSlug(String idOrSlug, Long actorUserId, boolean actorIsAdmin) {
        return toolMapper.toResponse(findVisibleByIdOrSlug(idOrSlug, actorUserId, actorIsAdmin));
    }

    @Override
    @Transactional
    public ToolResponse getDetailByIdOrSlug(String idOrSlug, Long actorUserId, boolean actorIsAdmin) {
        Tool tool = findVisibleByIdOrSlug(idOrSlug, actorUserId, actorIsAdmin);
        boolean published = tool.getStatus() == ToolStatus.PUBLISHED;
        boolean owner = actorUserId != null && actorUserId.equals(tool.getOwnerId());
        if (published && !owner) {
            if (actorUserId == null) {
                toolRepository.incrementPublishedViewCount(tool.getId(), ToolStatus.PUBLISHED);
            } else {
                toolRepository.incrementPublishedViewCountExcludingOwner(
                        tool.getId(), ToolStatus.PUBLISHED, actorUserId);
            }
        }
        return toolMapper.toResponse(tool);
    }

    private Tool findVisibleByIdOrSlug(String idOrSlug, Long actorUserId, boolean actorIsAdmin) {
        return requireVisible(findByIdOrSlug(idOrSlug), actorUserId, actorIsAdmin);
    }

    private Tool requireVisible(Tool tool, Long actorUserId, boolean actorIsAdmin) {
        boolean publicTool = tool.getStatus() == ToolStatus.PUBLISHED;
        boolean owner = actorUserId != null && actorUserId.equals(tool.getOwnerId());
        if (!publicTool && !owner && !actorIsAdmin) {
            throw new ResourceNotFoundException("Tool not found: " + tool.getId());
        }
        return tool;
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
        Tool tool = toolRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool not found: " + id));
        entityManager.refresh(tool);
        assertCanMutate(tool, actorUserId, actorIsAdmin);
        if (tool.getStatus() != ToolStatus.DRAFT) {
            throw new InvalidStateTransitionException("Tool metadata can only be edited in draft status");
        }
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
